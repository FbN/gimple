# Gimple
Gimple is a small Dependency Injection Container for Groovy, Java and more generally for JVM. Consists of just one class ( ... maybe two ). Is inspired (and in many points carbon-copied) to the [PHP Pimple project]( http://pimple.sensiolabs.org/ )

Homepage: http://fbn.github.io/gimple/

## Features

* **Simple**: one (... maybe two) class.
* **Modern**: strong use of closure
* **Thread safty & efficency**: powered by [Guava Stripe](https://code.google.com/p/guava-libraries/wiki/StripedExplained). Tested with [thread-safe.org](http://thread-safe.org/).
* **Nice API**: the container is an LinkedHashMap extension. In groovy you can use it like a Map, [:].
* **Prototype / Singleton**: define singleton o prototype scoped services or parameters.

## Why!?

Java, and more generally JVM, already got many dependency injection containers ( spring framework, google guice, picocontainer )
but all of them are heavy libraries composed of dozens of classes. 

This is a one class!

## Usage

Gimple is published via Maven:
* Maven repository: https://github.com/FbN/mvn/raw/master/
* Maven artifact: com.github.gimple Gimple 0.1.2

If you use gradle simply:

```groovy
repositories {
    maven {
        url 'https://github.com/FbN/mvn/raw/master/'
    }
} 
dependencies {
    compile 'com.github.gimple:Gimple:0.1.2'
}
```

## API

### Instantiate
```groovy
def container = new gimple.Container()
```

### Defining Services
A service is an object that does something as part of a larger system. 
Examples of services: a database connection, a templating engine, or a mailer. 
Almost any global object can be a service.
Services are defined by closure that return an instance of an object:

```groovy
container['sessionStorage'] = { c->
    new SessionStorage('SESSION_ID')
}

container['session'] = { c->
    new Session(c['sessionStorage'])
}
```
Notice that closure has access to the current container instance, allowing references to other services or parameters.

As objects are only created when you get them, the order of the definitions does not matter.

Using the defined services is also very easy:

```groovy
def session = container['session']
```

### Defining Factory Services

By default, each time you get a service, Gimple returns the same instance of it. 
If you want a different instance to be returned for all calls, wrap your closure with the factory() method

```groovy
container['session'] = container.factory( { c->
    new Session(c['sessionStorage'])
    })
```
Now, each call to container['session'] returns a new instance of the session.

### Defining Parameters

Defining a parameter allows to ease the configuration of your container from the outside and to store global values:
```groovy
container['cookieName'] = 'SESSION_ID'
container['sessionStorageClass'] = 'SessionStorage'
```
If not is a closure is a parameter.

If you change the sessionStorage service definition like below:

```groovy
container['sessionStorage'] = { c->
    new "${c['sessionStorageClass']}"(c['cookieName'])
}
```

### Protecting Parameters
Because Gimple sees clsure as service definitions, you need to wrap closure with the protect() method to store them as parameters:

```groovy
container['randomFunc'] = container.protect({ new Random().nextInt() })
```

### Modifying Services after Definition
You can replace a service simple reassigning a new value to id (the service must not be already used).
In some cases you may want to modify a service definition after it has been defined without replacing it. You can see it as a filter applied after the original service.
You can use the extend() method to define additional code to be run on your service just after it is created:

```groovy
container['sessionStorage'] = { c->
    new "${c['sessionStorageClass']}"(c['cookieName'])
};

container->extend('sessionStorage', {storage, c->
    ...
    do anything with storage and c
    ...

    storage
})
```
The first argument is the name of the service to extend, the second a function that gets access to the object instance and the container.

### Extending a Container

If you use the same libraries over and over, you might want to reuse some services from one project to the next one; package your services into a provider by implementing gimple.ServiceProviderInterface:

```groovy
class FooProvider implements gimple.ServiceProviderInterface
{
    def register(Container gimple)
    {
        // register some services and parameters
        // on gimple
    }
}
```

Then, register the provider on a Container:

```groovy
gimple.register(new FooProvider())
```

### Fetching the Service Creation Function
When you access an object, Gimple automatically calls the closure that you defined, which creates the service object for you. If you want to get raw access to the closure, you can use the raw() method:

```groovy
container['session'] = { c->
    new Session(c['sessionStorage'])
};

def sessionFunction = container.raw('session')
```

## Authors and Contributors
@fbn Fabiano Taioli  ftaioli@gmail.com
@Opalo Maciek Opała

