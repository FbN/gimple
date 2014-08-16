package gimple

import groovy.lang.Closure
import groovyx.gpars.agent.Agent

class Container extends LinkedHashMap<String, Object> {

	private def factories = [] as Set
	private def protectedValues = [] as Set
	private def frozen = [] as Set
	private def raw = [:]		

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

		def raw = super.get(id)
		def val = raw(this)
		this[id] = val
		this.raw[id] = raw
		this.frozen << id
		val
	}
	
	def getAt(String id){
		this.get(id)
	}
	
	def put(String id, Object value){

		assert !this.frozen.contains(id), "Cannot override frozen service '$id'."
		
		super.put(id, value)
		
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
		if (this.containsKey(id)) {
			this.factories.remove(super.get(id))
			this.protectedValues.remove(super.get(id))
			super.remove(id)
			this.frozen.remove(id)
			this.raw.remove(id)
			return true
		}
		return false
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
	  assert this.containsKey(id), "Identifier '$id' is not defined."
		
	  if(this.raw.containsKey(id)) {
		return this.raw(id)
	  	}
	  
	  super.get(id)
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
		 
		 assert this.containsKey(id), "Identifier '$id' is not defined."
		 
		 assert super.get(id) instanceof Closure, "Identifier '$id' does not contain a closure."
		 	 		 		 
		 def factory = super.get(id)
		 
		 def extended = {c-> 
			 callable(factory(c), c)
		 } 
	 
		 if (this.factories.contains(factory)) {
			 this.factories.remove(factory)
			 this.factories.add(extended)
		 }
	 
		 this[id] = extended
		 
	 }
	 
	 /**
	  * Returns all defined value names.
	  *
	  * @return array An array of value names
	  */
	  def keys(){ keySet() }
	  
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
