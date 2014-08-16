package gimple

import groovy.util.GroovyTestCase
import gimple.fixtures.Service

class ContainerTestCase extends GroovyTestCase {
	
	void testInit(){		
		
		assert new Container() == [:]
		
	}
	
	void testWithString(){
		
		def gimple = new Container()
		
		gimple['param'] = 'value'
		
		assert gimple['param'] == 'value'
		
	}
	
	void testWithClosure(){
	
		def gimple = new Container()
		gimple['service'] = {
			new Service()
			}
		assert gimple['service'] instanceof Service
	}
	
	void testServicesShouldBeDifferent(){
		
		def gimple = new Container()
		gimple['service'] = gimple.factory({
				new Service()
			})
		def serviceOne = gimple['service']
		assert serviceOne instanceof Service 
	
		def serviceTwo = gimple['service']	
		assert serviceTwo instanceof Service
		
		assertNotSame serviceOne, serviceTwo
	}
	
	void testShouldPassContainerAsParameter(){
		
		def gimple = new Container()
		
		gimple['service'] = {
			new Service()
			}
	
		gimple['container'] = {container->
			container
			}
		
		assertNotSame gimple, gimple['service']	
		assertSame gimple, gimple['container']
		
	}
	
	void testIsset(){
			
		def gimple = new Container()
		
		gimple['param'] = 'value'
		gimple['service'] = { new Service() }
		gimple['null'] = null
		
		assertTrue gimple.containsKey('param')
		assertTrue gimple.containsKey('service')
		assertTrue gimple.containsKey('null')
		assertFalse gimple.containsKey('non_existent')
		
	}
	
	void testConstructorInjection(){
		
		def params = [param: 'value']
		def gimple = new Container(params)
		assertSame params['param'], gimple['param']
		
	}
	
	void testOffsetGetValidatesKeyIsPresent(){
		
		def gimple = new Container()
		shouldFail {gimple['foo']}
		
	}
	
	void testOffsetGetHonorsNullValues(){
	
		def gimple = new Container()
		gimple['foo'] = null
		assertNull gimple['foo'] 
		
	}
	
	
}
