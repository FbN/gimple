package gimple

import groovy.lang.Closure

import java.util.concurrent.locks.ReadWriteLock

import com.google.common.util.concurrent.Striped
import com.sun.xml.internal.ws.client.sei.ValueSetter.ReturnValue;

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
	def get(String id){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			println "#1 whait for read lock "+id
			rwLock.readLock().lock()
			println "#2 locked read "+id
			
			if (!this.containsKey(id)) {
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
			println "#2.1 Unlock read "+id
			rwLock.readLock().unlock()
		}
		
		try {
			println "#2.2 whait for read lock "+id
			rwLock.writeLock().lock()
			def raw = super.get(id)
			def val = raw(this)
			this[id] = val
			this.raw[id] = raw
			this.frozen << id
			return val			
		} finally {
			println "#3 Unlock write "+id
			rwLock.writeLock().unlock()
		}
		
	}

	def getAt(String id){
		this.get(id)
	}

	def put(String id, Object value){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			println "#4 whait for write lock "+id
			rwLock.writeLock().lock()
			println "#5 locked write "+id
			
			assert !this.frozen.contains(id), "Cannot override frozen service '$id'."
			super.put(id, value)
			
		} finally {
			println "#6 unlock write "+id
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
	def remove(String id){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			println "#7 whait for write lock "+id
			rwLock.writeLock().lock()
			println "#8 locked write "+id
			if (this.containsKey(id)) {
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
			println "#9 unlocked write "+id
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
	def raw(String id) {

		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			println "#10 whait for read lock "+id
			rwLock.readLock().lock()
			println "#11 locked read "+id
			
			assert this.containsKey(id), "Identifier '$id' is not defined."

			if(this.raw.containsKey(id)) {
				return this.raw[id]
			}

			super.get(id)
		}	finally {
			rwLock.readLock().unlock()
			println "#12 unlocked read "+id
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
	def extend(id, Closure callable){

		def extended

		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			println "#13 whait for read lock "+id
			rwLock.writeLock().lock()
			println "#14 locked read "+id
			assert this.containsKey(id), "Identifier '$id' is not defined."

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
			println "#15 unlocked read "+id
		}
				
		this[id] = extended
	}

	/**
	 * Returns all defined value names.
	 *
	 * @return array An array of value names
	 */
	def keys(){
		keySet()
	}

	/**
	 * Registers a service provider.
	 *
	 * @param ServiceProviderInterface $provider A ServiceProviderInterface instance
	 * @param array $values An array of values that customizes the provider
	 *
	 * @return static
	 */
	def register(ServiceProviderInterface provider, values = [:]){
		
		provider.register(this)

		for ( e in values ) {
			this[e.key] = e.value
		}

		this
		
	}
}
