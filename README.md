# quarkus-camel-strimzi-trivial
 
Version 1.0.0
Kevin Boone, November 2022

## What is this?

This is the simplest Camel/Quarkus example I could think of, that demonstrates
consuming messages from a Strimzi Kafka broker, from outside the
Kubernetes/Openshift cluster.

Quarkus has its own Kafka support which is well documented. However, Camel
users are, I guess, more likely to prefer the Camel way of doing things. Camel
has its own way of configuring access to the Kafka server.

The application just consumes from a specific Kafka topic, and dumps to
standard out some information about each message. Note that the application
does not set any particular consumer offset, so it will only consume _new_
messages, that is, those produced since the last consumption from the same
topic.

The sample is slightly non-trivial in that it will have to supply a username
and password -- but it's unlikely that any practical installation would be
completely unauthenticated. 

Consuming messages from outside the cluster requires the use of an
Ingress/Route, which will _always_ be TLS-encrypted. For the sake of
completeness I note that "outside" the cluster might amount to an 
application in a different namespace on the same Kubernetes installation
-- whether namespaces can communicate without going via an Ingress
depends on the installation's network policies.

Because the communication will be over TLS, the client (this Quarkus
application) will have to validate the server's TLS certificate.  If the
Strimzi Kafka pods present a certificate that is validated by a well-known
provider, the JVM will probably recognize it, and no additional validation will
be required. In practice, though, the Quarkus application will usually have to
validate a certificate. 

## Prerequisites

- Java JDK 11 or later
- A running Strimzi cluster on OpenShift or Kubernetes (or Red Hat AMQ Streams) 
- Some way to put publish message to Kafka (e.g., `rsh` to a running Kafka pod and run `kafka-console-producer.sh`) 

## Configuration

All configuration parameters are in `src/resources/application.properties`.
These include the Kafka bootstrap URL and topic name, and the authentication
and encryption set-up.

The configuration properties are, I think, well documented. You'll need to
modify at least:

- The Kafka bootstrap URL, which will be a Route/Ingress hostname
- The password for the server certificate. Although there's no compelling 
  reason to password-protect a server certificate, Strimzi generates the
  certificate with password protection at set-up time. It's easier just to
  give the password, than to remove the password from the certificate
- The Kafka topic from which to consume
- The user credentials that will allow this topic to be read 

*Note*: the configuration file externalizes a bunch of other settings related
to TLS. However, so far as I know, the settings I've used in this example are
the only ones supported by Strimzi, when using user/password authentication
over TLS. 

## Running

To run the self-contained JAR (assuming you have configured it to be built):

   java -jar target/quarkus-camel-kafka-trivial-1.0.0-runner.jar

On Linux, you can set configuration values at run time using environment
variables, like this:

    KAFKA_BOOTSTRAP_URL=my_kafka:9092 java -jar target/...

To run in development mode, use:

    mvn quarkus:dev

A useful feature of development mode, apart from enabling remote debugging,
is that allows the log level to be changed using a keypress.

To test that the application consuming, you'll need some way to post
messages to the relevant broker topic. If you don't have such a 
thing, you could log into any broker pod and run the command-line
producer like this:

    $ /opt/kafka/bin/kafka-console-producer.sh \
       --bootstrap-server :9092 --topic my-topic 
       --property parse.key=true --property key.separator="#"

The Quarkus application should log a message for each message produced.
In practice, the difficulty in this set-up is the TLS configuration; if
the Quarkus application connects to Strimzi without error messages, it
will probably consume messages with no further problems.

## Strimzi set-up

In order to allow access to clients outside the OpenShift/Kubernetes
cluster, Kafka will need to be set up to expose the TLS listener as
a Route/Ingress. Also, authentication and authorization will probably
be enabled. The relevant sections of the Kafka configuration, in 
YAML format, will look something like this:


kafka:
    authorization:
      disableTlsHostnameVerification: true
      type: simple

  listeners:
      - authentication:
          type: scram-sha-512
        name: plain
        port: 9092
        tls: false
        type: internal
      - authentication:
          type: scram-sha-512
        name: tls
        port: 9093
        tls: true
        type: route

This is the configuration for user/password authentication over TLS; 
an alternative is to use client certificates to provide the user 
identify (not described here).

## Getting the Strimzi TLS certificate for verification

Unless the Strimzi installation uses a certificate that is validated
by a well-known certificate authority, you'll have to extract the 
Strimzi cluster certificate and import it into the Quarkus application's
runtime. 

In general, one top-level certificate will sign all TLS certificates
generated for Kafka listeners. This certificate is in a secret
called `[cluster_name]-cluster-ca-cert`. If the Strimzi cluster
is called `my-cluster`, you can extract the certificate, in 
PKCS12 format, like this:

    $ oc get secret my-cluster-cluster-ca-cert \
          -o jsonpath="{.data['ca\.p12']}" | base64 -d > truststore.p12

The certificate will be protected by a password, that is auto-generated
at the same time as the certificate. This can be extracted 
as follows:

    $ oc get secret my-cluster-cluster-ca-cert \
          -o jsonpath="{.data['ca\.password']}" | base64 -d

If you've installed a custom certificate in the Strimzi cluster, then
you'll need to obtain the top-level CA certificate to import into
the Quarkus application.

In either case, I'm assuming that the file is called `truststore.p12`, and is
in the working directory of the application.  Java JKS keystores can also be
used with (as I recall) no change in configuration.

Incidentally, the Kafka client runtime only supports loading server 
certificates from a filesystem location. The certificate can't, for
example, be embedded in the application's JAR file. This is a limitation
of Kafka, not Quarkus or Camel.


