package gimple

import gimple.fixtures.Service
import spock.lang.Specification

class ContainerSpec extends Specification {

    final static badServiceParameter = [123]

    def 'test init'() {
        expect:
        new Container() == [:]
    }

    def 'test with string'() {
        given:
        def gimple = new Container()

        when:
        gimple['param'] = 'value'

        then:
        gimple['param'] == 'value'
    }

    def 'test with closure'() {
        given:
        def gimple = new Container()

        when:
        gimple['service'] = {
            new Service()
        }

        then:
        gimple['service'] instanceof Service
    }

    def 'test services should be different'() {
        given:
        def gimple = new Container()

        and:
        gimple['service'] = gimple.factory({
            new Service()
        })

        and:
        def serviceOne = gimple['service']
        def serviceTwo = gimple['service']

        expect:
        serviceOne instanceof Service
        serviceTwo instanceof Service
        serviceOne != serviceTwo
    }

    def 'test should pass container as a parameter'() {
        given:
        def gimple = new Container()

        and:
        gimple['service'] = {
            new Service()
        }

        and:
        gimple['container'] = { container ->
            container
        }

        expect:
        gimple != gimple['service']
        gimple == gimple['container']
    }

    def 'test is set'() {
        given:
        def gimple = new Container()

        and:
        gimple['param'] = 'value'
        gimple['service'] = { new Service() }
        gimple['null'] = null

        expect:
        gimple.containsKey('param')
        gimple.containsKey('service')
        gimple.containsKey('null')
        !gimple.containsKey('non_existent')
    }

    def 'test constructor injection'() {
        given:
        def params = [param: 'value']

        and:
        def gimple = new Container(params)

        expect:
        params['param'] == gimple['param']
    }

    def 'test offset get validates key is present'() {
        given:
        def gimple = new Container()

        when:
        gimple['foo']

        then:
        def e = thrown(RuntimeException)
        e.message == "Identifier 'foo' is not defined."
    }

    def 'test offset get honors null values'() {
        given:
        def gimple = new Container()

        when:
        gimple['foo'] = null

        then:
        !gimple['foo']
    }

    def 'test unset'() {
        given:
        def gimple = new Container()

        and:
        gimple['param'] = 'value'
        gimple['service'] = { new Service() }
        gimple.remove('param')
        gimple.remove('service')

        when:
        gimple['param']

        then:
        def e = thrown(RuntimeException)
        e.message == "Identifier 'param' is not defined."

        when:
        gimple['service']

        then:
        e = thrown(RuntimeException)
        e.message == "Identifier 'service' is not defined."
    }

    def 'test share'() {
        given:
        def gimple = new Container()
        gimple['shared_service'] = new Service()

        when:
        def serviceOne = gimple['shared_service']
        def serviceTwo = gimple['shared_service']

        then:
        serviceOne instanceof Service
        serviceTwo instanceof Service
        serviceOne == serviceTwo
    }

    def 'test protect'() {
        given:
        def gimple = new Container()

        and:
        def callable = {}

        when:
        gimple['protected'] = gimple.protect(callable)

        then:
        callable == gimple['protected']
    }

    def 'test private field as a parameter value'() {
        given:
        def gimple = new Container()

        when:
        gimple['frozen'] = { 'xxx' }

        then:
        'xxx' == gimple['frozen']
    }

    def 'test raw'() {
        given:
        def gimple = new Container()

        when:
        def definition = gimple.factory({ 'foo' })
        gimple['service'] = definition

        then:
        definition == gimple.raw('service')
    }

    def 'test raw honors null values'() {
        given:
        def gimple = new Container()

        when:
        gimple['foo'] = null

        then:
        !gimple.raw('foo')
    }

    def 'test raw validates key is present'() {
        given:
        def gimple = new Container()

        when:
        gimple.raw('foo')

        then:
        def e = thrown(AssertionError)
        e.message.startsWith("Identifier 'foo' is not defined.")
    }

    def 'test extend'() {
        given:
        def gimple = new Container()

        and:
        def extendCallable = { value, container ->
            def service = new Service()
            service.value = value
            service
        }

        and:
        gimple['shared_service'] = { new Service() }
        gimple['factory_service'] = gimple.factory({ new Service() })
        gimple.extend('shared_service', extendCallable)

        when:
        def serviceOne = gimple['shared_service']
        def serviceTwo = gimple['shared_service']

        then:
        serviceOne instanceof Service
        serviceTwo instanceof Service

        then:
        serviceOne == serviceTwo
        serviceOne.value == serviceTwo.value

        when:
        gimple.extend('factory_service', extendCallable)
        serviceOne = gimple['factory_service']

        then:
        serviceOne instanceof Service

        when:
        serviceTwo = gimple['factory_service']

        then:
        serviceTwo instanceof Service

        then:
        serviceOne != serviceTwo
        serviceOne.value != serviceTwo.value
    }

    def 'test extend does not leak with factories'() {
        given:
        def gimple = new Container()

        and:
        gimple['foo'] = gimple.factory({})
        gimple['foo'] = gimple.extend('foo', { foo, g -> })

        when:
        gimple.remove('foo')

        then:
        gimple.isEmpty()

        when:
        def f = gimple.getClass().getDeclaredField("factories")
        f.setAccessible(true)

        then:
        f.get(gimple).isEmpty()
    }

    def 'test extend validates key is present'() {
        given:
        def gimple = new Container()

        when:
        gimple.extend('foo', {})

        then:
        def e = thrown(AssertionError)
        e.message.startsWith("Identifier 'foo' is not defined.")
    }

    def 'test keys'() {
        given:
        def gimple = new Container()

        and:
        gimple['foo'] = 123
        gimple['bar'] = 123

        expect:
        ['foo', 'bar'] as Set == gimple.keySet()
    }

    def 'test factory fails for invalid service definitions'() {
        given:
        def gimple = new Container()

        when:
        gimple.factory(badServiceParameter)

        then:
        thrown(Exception)
    }

    def 'test protect fails for invalid service definitions'() {
        given:
        def gimple = new Container()

        when:
        gimple.protect(badServiceParameter)

        then:
        thrown(Exception)
    }

    def 'test extend fails for keys not containing service definitions'() {
        given:
        def gimple = new Container()

        and:
        gimple['foo'] = badServiceParameter

        when:
        gimple.extend('foo', {})

        then:
        thrown(AssertionError)
    }

    def 'test extend fails for invalid service definitions'() {
        given:
        def gimple = new Container()

        and:
        gimple['foo'] = {}

        when:
        gimple.extend('foo', badServiceParameter)

        then:
        thrown(Exception)
    }

    def 'test defining new service after freeze'() {
        given:
        def gimple = new Container()

        and:
        gimple['foo'] = { 'foo' }

        when:
        def foo = gimple['foo']
        gimple['bar'] = { 'bar' }

        then:
        'bar' == gimple['bar']
    }

    def 'test overriding service after freeze'() {
        given:
        def gimple = new Container()

        when:
        gimple['foo'] = { 'foo' }
        def foo = gimple['foo']
        gimple['foo'] = { 'bar' }

        then:
        thrown(AssertionError)
    }

    def 'test removing service after freeze'() {
        given:
        def gimple = new Container()

        when:
        gimple['foo'] = { 'foo' }
        def foo = gimple['foo']
        gimple.remove('foo')
        gimple['foo'] = { 'bar' }

        then:
        'bar' == gimple['foo']
    }

    def 'test extending service'() {
        given:
        def gimple = new Container()

        when:
        gimple['foo'] = { 'foo' }
        gimple['foo'] = gimple.extend('foo', { foo, app -> "${foo}.bar" })
        gimple['foo'] = gimple.extend('foo', { foo, app -> "${foo}.baz" })

        then:
        'foo.bar.baz' == gimple['foo']
    }
}
