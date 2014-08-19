package gimple

import groovy.lang.Closure

import java.util.concurrent.locks.ReadWriteLock
import groovy.transform.WithWriteLock
import groovy.transform.WithReadLock
import com.google.common.util.concurrent.Striped

/**
 * 
 * @author Fabiano Taioli
 * 
 * Ovverrided methods are thread safe. 
 * 
 * Not ovverrided methods are not thread safe. 
 *
 */
class Container extends LinkedHashMap<String, Object> {

	private def factories = [] as Set
	private def protectedValues = [] as Set
	private def frozen = [] as Set
	private def raw = [:]	

	private final Striped<ReadWriteLock> rwLockStripes = Striped.readWriteLock(Runtime.getRuntime().availableProcessors())
	
	/**
	 * Gets a parameter or an object.
	 *
	 * @param string $id The unique identifier for the parameter or object
	 *
	 * @return mixed The value of the parameter or an object
	 *
	 * @throws \InvalidArgumentException if the identifier is not defined
	 */
	@WithReadLock
	def get(String id){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.readLock().lock()
			
			if (!super.containsKey(id)) {
				throw new RuntimeException("Identifier '$id' is not defined.")
			}
	
			if ( this.raw.containsKey(id)
			|| this.protectedValues.contains(super.get(id))
			|| !(super.get(id) instanceof Closure)
			) {
				return super.get(id)
			}
	
			if (this.factories.contains(super.get(id))) {
				return super.get(id).call(this)
			}		
		} finally {
			rwLock.readLock().unlock()
		}
		
		try {
			rwLock.writeLock().lock()
			def raw = super.get(id)
			def val = raw(this)
			this[id] = val
			this.raw[id] = raw
			this.frozen << id
			return val			
		} finally {
			rwLock.writeLock().unlock()
		}
		
	}

	
	def getAt(String id){
		get(id)
	}
	
	@WithReadLock
	def put(String id, Object value){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.writeLock().lock()			
			assert !this.frozen.contains(id), "Cannot override frozen service '$id'."
			super.put(id, value)			
		} finally {
			rwLock.writeLock().unlock()
		}
	}

	def putAt(String id, Object value){
		this.put(id, value)
	}

	/**
	 * Unsets a parameter or an object.
	 *
	 * @param string $id The unique identifier for the parameter or object
	 */
	@WithReadLock
	def remove(String id){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.writeLock().lock()
			if (super.containsKey(id)) {
				this.factories.remove(super.get(id))
				this.protectedValues.remove(super.get(id))
				super.remove(id)
				this.frozen.remove(id)
				this.raw.remove(id)
				return true
			}
			return false
		} finally {
			rwLock.writeLock().unlock()
		}
	}


	/**
	 * Marks a callable as being a factory service.
	 *
	 * @param callable $callable A service definition to be used as a factory
	 *
	 * @return callable The passed callable
	 *
	 * @throws \InvalidArgumentException Service definition has to be a closure of an invokable object
	 */
	@WithReadLock
	def factory(Closure callable) {

		this.factories.add(callable)

		callable
	}

	/**
	 * Protects a callable from being interpreted as a service.
	 *
	 * This is useful when you want to store a callable as a parameter.
	 *
	 * @param callable $callable A callable to protect from being evaluated
	 *
	 * @return callable The passed callable
	 *
	 * @throws \InvalidArgumentException Service definition has to be a closure of an invokable object
	 */
	@WithReadLock
	def protect(Closure callable){

		this.protectedValues.add(callable)

		callable
	}

	/**
	 * Gets a parameter or the closure defining an object.
	 *
	 * @param string $id The unique identifier for the parameter or object
	 *
	 * @return mixed The value of the parameter or the closure defining an object
	 *
	 * @throws \InvalidArgumentException if the identifier is not defined
	 */
	@WithReadLock
	def raw(String id) {

		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.readLock().lock()
			
			assert super.containsKey(id), "Identifier '$id' is not defined."

			if(this.raw.containsKey(id)) {
				return this.raw[id]
			}

			super.get(id)
		}	finally {
			rwLock.readLock().unlock()
		}
	}

	/**
	 * Extends an object definition.
	 *
	 * Useful when you want to extend an existing object definition,
	 * without necessarily loading that object.
	 *
	 * @param string $id The unique identifier for the object
	 * @param callable $callable A service definition to extend the original
	 *
	 * @return callable The wrapped callable
	 *
	 * @throws \InvalidArgumentException if the identifier is not defined or not a service definition
	 */
	@WithReadLock
	def extend(id, Closure callable){

		def extended

		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.writeLock().lock()
			assert super.containsKey(id), "Identifier '$id' is not defined."

			assert super.get(id) instanceof Closure, "Identifier '$id' does not contain a closure."

			def factory = super.get(id)

			extended = {c->
				callable(factory(c), c)
			}

			if (this.factories.contains(factory)) {
				this.factories.remove(factory)
				this.factories.add(extended)
			}					
		}	finally {
			rwLock.writeLock().unlock()
		}
				
		this[id] = extended
	}

	/**
	 * Registers a service provider.
	 *
	 * @param ServiceProviderInterface $provider A ServiceProviderInterface instance
	 * @param array $values An array of values that customizes the provider
	 *
	 * @return static
	 */
	@WithReadLock
	def register(ServiceProviderInterface provider, values = [:]){
		
		provider.register(this)

		for ( e in values ) {
			this[e.key] = e.value
		}

		this
		
	}
	
	@WithWriteLock
	void clear(){ super.clear() }
	
	@WithWriteLock
	boolean	containsValue(Object value){ super.containsValue(value) }
	
	@WithWriteLock
	Object	clone(){ super.clone() }
	
	@WithWriteLock
	Set<Map.Entry<String, Object>>	entrySet() { super.entrySet() }
	
	@WithWriteLock
	boolean	isEmpty(){ super.isEmpty() }
	
	@WithWriteLock
	Set<String>	keySet(){ super.keySet() }
	
	@WithReadLock
	void	putAll(Map<? extends String,? extends Object> m){ 		
		for ( e in m ) {
			this[e.key] = e.value
		}		
	}
	
	@WithReadLock
	boolean containsKey(String id){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.readLock().lock()
			super.containsKey(id)
		} finally {
			rwLock.readLock().unlock()
		}
	}
	
	@WithWriteLock
	int	size(){ super.size() }
	
	@WithWriteLock
	Collection<Object>	values(){ super.values() }
	
	@WithWriteLock
	boolean equals(Object o){ super.equals(o) }
	
	@WithWriteLock
	int hashCode(){ super.hashCode() }
	
	@WithWriteLock
	String toString(){ super.toString() }	
	
}
