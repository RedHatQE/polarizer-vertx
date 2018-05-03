# What is polarizer-vertx?

polarizer-vertx is a [Vertx][-vertx] based microservice architecture for use at Red Hat.  It currently implements the following
service Verticles:

- Polarion XUnit Importer: to upload a team's test suite results
- TestCase Importer: to create/edit TestCase's both in Polarion
- Mapping Generator: to create/update a mapping.json file (used by the above two) to map testcases to Polarion IDs
- Jar Reflector: Given a java jar annotated with Polarizer annotations, obtain all the metadata for tests
- Message Bus listener: a JMS to websocket bridge

## What is it used for?

See the rationale for polarizer for a deeper dive.  

## Why vertx?

Because I think it's a good choice for building microservices.  First, the asynchronous choice was a natural fit for rxjava, so the 
Verticles in polarizer-vertx are all 'Rx-ified'.  Secondly, it's not bloated, like using Wildfly.  Spring boot was another possible
option, but I liked the holistic feel of Vertx.

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

**FIXME**

Right now, polarizer-vertx builds (other than through me, the author) are broken due to how the build.gradle requires authentication
credentials for artifactory.  The move to artifactory was caused by some strange bug from vertx, and I was unable to deploy to it to
maven central.  

**In Progress**



[-vertx]: http://vertx.io/
[-polarizer]: https://github.com/rarebreed/polarizer
[-mercury]: https://github.com/rarebreed/mercury
