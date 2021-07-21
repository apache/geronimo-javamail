
Building
========

To build you will need:

 * J2SE SDK 1.5+ (http://java.sun.com/j2se/1.5/)
 * Maven 2.0.7+ (http://maven.apache.org)

To build all changes incrementally:

    mvn install

To perform clean builds, which are sometimes needed after some changes to the
source tree:

    mvn clean install


SSL/TLS Protocols used for Mail Connection
========

## Default Behaviour

By default, the implementation checks for the presence of `ssl.protocols`. If this property is not set, the SSL/TLS socket is created with JVM defaults.

## Enable Custom SSL Protocols

The property `ssl.protocols` can be used to specify a list of protocols, which should be enabled for the underlying SSL/TLS socket.
It accepts a list of protocols with a whitespace as delimiter.

### Example for SMTP

To support TLSv1+ the following property can be set:

```
mail.smtp.ssl.protocols=TLSv1 TLSv1.1 TLSv1.2 TLSv1.3
``

## Using a Custom SSL Socket Factory (via Reflection)

The property `ssl.socketFactory.class` can be used to specify a custom SSL socket factory, which is used to create the underlying SSL/TLS socket.
This allows full control of supported cyphers or protocols.

#### Example for SMTP

```
mail.smtp.ssl.socketFactory.class=my.custom.CustomSSLSocketFactory
``

## Using a Custom SSL Socket Factory (as pre-configured instance)

The property `ssl.socketFactory` can be used to specify a pre-configured custom SSL socket factory, which is used to create the underlying SSL/TLS socket.
In this context, the instance has to be passed to the `Properties` of the related `MailSession`. This allows full control of supported cyphers or protocols.

# Cipher suites

## Default Behaviour

By default, the implementation checks for the presence of `ssl.ciphersuites`. If this property is not set, the SSL/TLS socket is created with all supported ciphers of the given SSL Socket.

## Enable Custom Cipher Suites

The property `ssl.ciphersuites` can be used to specify a list of ciphers, which should be enabled for the underlying SSL/TLS socket.
It accepts a list of ciphers with a whitespace as delimiter. You have to ensure, that the listed cipher suites are supported by the given JVM.

### Example for SMTP

To support only `TLS_AES_128_GCM_SHA256` and `TLS_AES_256_GCM_SHA384` the following property can be set:

```
mail.smtp.ssl.ciphersuites=TLS_AES_128_GCM_SHA256 TLS_AES_256_GCM_SHA384
``

## Using a Custom SSL Socket Factory (via Reflection)

The property `ssl.socketFactory.class` can be used to specify a custom SSL socket factory, which is used to create the underlying SSL/TLS socket.
This allows full control of supported cyphers or protocols.

#### Example for SMTP

```
mail.smtp.ssl.socketFactory.class=my.custom.CustomSSLSocketFactory
``

## Using a Custom SSL Socket Factory (as pre-configured instance)

The property `ssl.socketFactory` can be used to specify a pre-configured custom SSL socket factory, which is used to create the underlying SSL/TLS socket.
In this context, the instance has to be passed to the `Properties` of the related `MailSession`. This allows full control of supported cyphers or protocols.