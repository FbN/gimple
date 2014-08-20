package gimple

import groovy.lang.Closure

import java.util.concurrent.locks.ReadWriteLock
import groovy.transform.WithWriteLock
import groovy.transform.WithReadLock
import com.google.common.util.concurrent.Striped

/**
 * @author Fabiano Taioli
 * 
 * Simple Groovy Dependecy Injection Container
 * 
 * Features:
 *   - Simple: one (maybe two) class.
 *   - Modern: strong use of closure.
 *   - Thread safe efficency: powered by Guava Stripe. Tested with thread-safe.org.
 *   - Inspired by PHP Pimple: in many places a mere traduction from PHP to Groovy
 *   - Grooy: writen in grooy, usable in any java or jvm based project.
 *   - Nice api: the container is an LinkedHashMap extension.
 *   - Prototype / Singleton: define singleton o prototype scoped services or parameters.
 *   - Minimum dependecy: java - groovy - guava
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
	 * @param id The unique identifier for the parameter or object
	 *
	 * @return An object
	 *
	 * @throws RuntimeException if the identifier is not defined
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

	/**
	 * Operator [] overloading. Call the get method
	 * 
	 * @param id The unique identifier for the parameter or object
	 * @return An object
	 */
	def getAt(String id){
		get(id)
	}
	
	/**
	 * 
	 * @param id The unique identifier for the parameter or object
	 * @param value An object or the closure for object initialization
	 * @return the previous value associated with key, or null if there was no mapping for key. (A null return can also indicate that the map previously associated null with key.)
	 */
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

	/**
	 * Operator []= overloading. Call the put method
	 *
	 * @param id The unique identifier for the parameter or object
	 * @param value An object or closure for object instantiation
	 * @return An object
	 */
	def putAt(String id, Object value){
		this.put(id, value)
	}

	/**
	 * Unsets a parameter or an object.
	 *
	 * @param id The unique identifier for the parameter or object
	 * @return the previous value associated with key, or null if there was no mapping for key. (A null return can also indicate that the map previously associated null with key.)
	 */
	@WithReadLock
	def remove(String id){
		ReadWriteLock rwLock = this.rwLockStripes.get(id)
		try {
			rwLock.writeLock().lock()
			if (super.containsKey(id)) {
				this.factories.remove(super.get(id))
				this.protectedValues.remove(super.get(id))
				this.frozen.remove(id)
				this.raw.remove(id)
				super.remove(id)
			}
		} finally {
			rwLock.writeLock().unlock()
		}
	}


	/**
	 * Marks a callable as being a factory service
	 * @param callable Service closure
	 * @return  Service closure
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
	 * @param callable A callable to protect from being evaluated
	 *
	 * @return callable The passed callable
	 * 
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
	 * @throws AssertionError if the identifier is not defined
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
	 * @param id The unique identifier for the object
	 * @param callable A service definition to extend the original
	 *
	 * @return The wrapped callable
	 *
	 * @throws AssertionError if the identifier is not defined or not a service definition
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
	 * @param provider A ServiceProviderInterface instance
	 * @param values An array of values that customizes the provider
	 *
	 * @return this
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
