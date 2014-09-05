package gimple

import gimple.fixtures.Service
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized)
class ContainerTestCase extends GroovyTestCase {

    @Test
    void testInit() {
        assert new Container() == [:]

    }

    @Test
    void testWithString() {

        def gimple = new Container()

        gimple['param'] = 'value'

        assert gimple['param'] == 'value'

    }

    @Test
    void testWithClosure() {

        def gimple = new Container()
        gimple['service'] = {
            new Service()
        }
        assert gimple['service'] instanceof Service
    }

    @Test
    void testServicesShouldBeDifferent() {

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

    @Test
    void testShouldPassContainerAsParameter() {

        def gimple = new Container()

        gimple['service'] = {
            new Service()
        }

        gimple['container'] = { container ->
            container
        }

        assertNotSame gimple, gimple['service']
        assertSame gimple, gimple['container']

    }

    @Test
    void testIsset() {

        def gimple = new Container()

        gimple['param'] = 'value'
        gimple['service'] = { new Service() }
        gimple['null'] = null

        assertTrue gimple.containsKey('param')
        assertTrue gimple.containsKey('service')
        assertTrue gimple.containsKey('null')
        assertFalse gimple.containsKey('non_existent')

    }

    @Test
    void testConstructorInjection() {

        def params = [param: 'value']
        def gimple = new Container(params)
        assertSame params['param'], gimple['param']

    }

    @Test
    void testOffsetGetValidatesKeyIsPresent() {

        def gimple = new Container()
        shouldFail { gimple['foo'] }

    }

    @Test
    void testOffsetGetHonorsNullValues() {

        def gimple = new Container()
        gimple['foo'] = null
        assertNull gimple['foo']

    }

    @Test
    void testUnset() {
        def gimple = new Container()
        gimple['param'] = 'value'
        gimple['service'] = { new Service() }
        gimple.remove('param')
        gimple.remove('service')
        shouldFail { gimple['param'] }
        shouldFail { gimple['service'] }
    }

    @Test
    void testShare() {

        def gimple = new Container()
        System.out.flush()
        gimple['shared_service'] = serviceParameter

        def serviceOne = gimple['shared_service']
        assert serviceOne instanceof Service

        def serviceTwo = gimple['shared_service']
        assert serviceTwo instanceof Service

        assertSame serviceOne, serviceTwo;

    }

    @Test
    void testProtect() {
        def gimple = new Container()
        gimple['protected'] = gimple.protect(serviceParameter)
        assertSame serviceParameter, gimple['protected']
    }

    @Test
    void testPrivateFieldAsParameterValue() {
        def gimple = new Container()
        gimple['frozen'] = { 'xxx' }
        assertSame 'xxx', gimple['frozen']
    }

    @Test
    void testRaw() {
        def gimple = new Container()
        def definition = gimple.factory({ 'foo' })
        gimple['service'] = definition
        assertSame definition, gimple.raw('service')
    }

    @Test
    void testRawHonorsNullValues() {
        def gimple = new Container()
        gimple['foo'] = null
        assertNull gimple.raw('foo')
    }

    @Test
    void testRawValidatesKeyIsPresent() {
        def gimple = new Container()
        shouldFail { gimple.raw('foo') }
    }

    @Test
    void testExtend() {

        def gimple = new Container()

        def extendCallable = { value, container ->
            def service = new Service()
            service.value = value
            service
        }

        gimple['shared_service'] = { new Service() }
        gimple['factory_service'] = gimple.factory({ new Service() })
        gimple.extend('shared_service', extendCallable)

        def serviceOne = gimple['shared_service']
        assert serviceOne instanceof Service

        def serviceTwo = gimple['shared_service']
        assert serviceTwo instanceof Service

        assertSame serviceOne, serviceTwo

        assertSame serviceOne.value, serviceTwo.value

        gimple.extend('factory_service', extendCallable)

        serviceOne = gimple['factory_service']
        assert serviceOne instanceof Service

        serviceTwo = gimple['factory_service']
        assert serviceTwo instanceof Service

        assertNotSame serviceOne, serviceTwo
        assertNotSame serviceOne.value, serviceTwo.value

    }

    @Test
    void testExtendDoesNotLeakWithFactories() {

        def gimple = new Container()
        gimple['foo'] = gimple.factory({})
        gimple['foo'] = gimple.extend('foo', { foo, g -> })

        gimple.remove('foo')

        assertTrue gimple.isEmpty()

        def f = gimple.getClass().getDeclaredField("factories")
        f.setAccessible(true)
        assertTrue f.get(gimple).isEmpty()

    }

    @Test
    void testExtendValidatesKeyIsPresent() {
        def gimple = new Container()
        shouldFail { gimple.extend('foo', {}) }
    }

    @Test
    void testKeys() {
        def gimple = new Container()
        gimple['foo'] = 123
        gimple['bar'] = 123
        assert ['foo', 'bar'] as Set == gimple.keySet()
    }

//	@Test
//	void settingAnInvokableObjectShouldTreatItAsFactory(){
//		def gimple = new Container();
//		$pimple['invokable'] = new Fixtures\Invokable();
//		$this->assertInstanceOf('Pimple\Tests\Fixtures\Service', $pimple['invokable']);
//	}

//	/** @test */
//	public function settingNonInvokableObjectShouldTreatItAsParameter()
//	{
//	$pimple = new Container();
//	$pimple['non_invokable'] = new Fixtures\NonInvokable();
//	$this->assertInstanceOf('Pimple\Tests\Fixtures\NonInvokable', $pimple['non_invokable']);
//	}

    @Test
    void testFactoryFailsForInvalidServiceDefinitions() {
        def gimple = new Container()
        shouldFail { gimple.factory(badServiceParameter) }
    }

    @Test
    void testProtectFailsForInvalidServiceDefinitions() {
        def gimple = new Container()
        shouldFail { gimple.protect(badServiceParameter) }
    }

    @Test
    void testExtendFailsForKeysNotContainingServiceDefinitions() {
        def gimple = new Container()
        gimple['foo'] = badServiceParameter
        shouldFail { gimple.extend('foo', {}) }
    }

    @Test
    void testExtendFailsForInvalidServiceDefinitions() {
        def gimple = new Container()
        gimple['foo'] = {}
        shouldFail { gimple.extend('foo', badServiceParameter) }
    }

    @Test
    void testDefiningNewServiceAfterFreeze() {
        def gimple = new Container()
        gimple['foo'] = { 'foo' }
        def foo = gimple['foo']
        gimple['bar'] = { 'bar' }
        assertSame 'bar', gimple['bar']
    }

    @Test
    void testOverridingServiceAfterFreeze() {
        def gimple = new Container()
        gimple['foo'] = { 'foo' }
        def foo = gimple['foo']
        shouldFail { gimple['foo'] = { 'bar' } }
    }

    @Test
    void testRemovingServiceAfterFreeze() {
        def gimple = new Container()
        gimple['foo'] = { 'foo' }
        def foo = gimple['foo']
        gimple.remove('foo')
        gimple['foo'] = { 'bar' }
        assertSame 'bar', gimple['foo']
    }

    @Test
    void testExtendingService() {
        def gimple = new Container()
        gimple['foo'] = { 'foo' }
        gimple['foo'] = gimple.extend('foo', { foo, app -> "${foo}.bar" })
        gimple['foo'] = gimple.extend('foo', { foo, app -> "${foo}.baz" })
        assert 'foo.bar.baz' == gimple['foo']
    }

    @Parameter
    public def serviceParameter

    public def badServiceParameter = [123]

    /**
     * Provider for service definitions
     */
    @Parameters
    public static def serviceDefinitionProvider() {
        [
                { value ->
                    def service = new Service()
                    service.value = value
                    service
                }
        ]
    }


}
