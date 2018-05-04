# What is polarizer-vertx?

polarizer-vertx is a [Vertx][-vertx] based microservice architecture for use at Red Hat. It currently implements the
following service Verticles:

- Polarion XUnit Importer: to upload a team's test suite results
- TestCase Importer: to create/edit TestCase's both in Polarion
- Mapping Generator: to create/update a mapping.json file (used by the above two) to map testcases to Polarion IDs
- Jar Reflector: Given a java jar annotated with Polarizer annotations, obtain all the metadata for tests
- Message Bus listener: a JMS to websocket bridge

## What is it used for?

See the rationale for polarizer for a deeper dive.  

## Why vertx?

Because I think it's a good choice for building microservices. First, the asynchronous choice was a natural fit for
rxjava, so the Verticles in polarizer-vertx are all 'Rx-ified'. Secondly, it's not bloated, like using Wildfly. Spring
boot was another possible option, but I liked the holistic feel of Vertx.

## Roadmap

Top things to do in no particular order:

- Implement service discovery mechanism
- Make this into a container and cluster it
- Implement TLS for:
  - Event bus
  - Websockets
  - Http verticles
- Figure out how to hook into LDAP for permissions, or OAuth 2
- More tests
  - [mercury][-mercury] front end web GUI to visualize and test the services
  

## How to build it

Building polarizer-vertx can be a little tricky, so here is how to do it.

Gradle is the build system for polarizer-vertx and all the other polarizer related projects:

- metadata
- reporter
- polarizer-umb
- polarizer

The dependency tree looks like this:

metadata -- depends on --> None
reporter -- depends on --> metadata
polarizer-umb -- depends on --> reporter
polarizer -- depends on ---> reporter
                         +-> polarizer-umb 
                         +-> metadata
                         
### Prerequisites to building any of the polarizer projects


When a developer wants to build the polarizer-vertx project (or any of the other polarizer related projects like
reporter, metadata, polarizer-umb or polarizer), then it is necessary to sign the artifact jars. To do this, gradle
needs information about the gpg keys.

The first step is to import the public gpg key from the public server, and then scp the secret key from auto-services.
Once the secret key is copied, it needs to be imported into the gpg keyring. Install gpg keys

```
gpg2 --receive-keys BF1DB607
scp root@auto-services.usersys.redhat.com:/var/www/html/polarizer/secring.gpg /some/path
chown your_user:your_group /some/path/secring.gpg
gpg --import /some/path/secring.gpg
```

Once you have the public gpg keys imported then you need to create a ~/.gradle/gradle.properties file that looks
something like this: gradle properties

```
signing.keyId=BF1DB607
signing.password=!love@!KO2012
signing.secretKeyRingFile=/home/USER/.gnupg/secring.gpg
```

### Building polarizer-vertx

Actually building polarizer-vertx is fairly simple:

```
cd /path/to/polarizer-vertx
./gradlew clean
./gradlew build
```

This will do everything necessary to build the jars, including a *fat* jar.

### Uploading a snapshot release

Releasing a snapshot jar is easy now:

```
./gradlew clean
./gradlew build
./gradlew uploadArchives  # or just ./gradlew uA
```

The build.gradle file in the project will look at the ~/.gradle/gradle.properties file so that it knows how to sign the
jar files and also the password to upload the jars to the snapshot repository.

### Publishing a new release version

Making a new release version is more complicated.  See the directions in https://github.com/RedHatQE/polarize#uploading-to-maven-central

[-vertx]: http://vertx.io/
[-polarizer]: https://github.com/rarebreed/polarizer
[-mercury]: https://github.com/rarebreed/mercury
