# How to build

Create a ~/gradle.properties file.  Gradle uses this so that it can read in key-val pairs so you can fill in hidden data

You will need to fill in:

- artifactory_user=your artifactory (oss.jfrog.org) user name (should be same as bintray user)
- artifactory_password=should be a UUID token key
- artifactory_contextUrl=url for artifactory

The hard thing to get here, is the artifactory_password.  When you first create a bintray account, you can find your API
key under your Eit Profile -> API Key.  You can use this API Key to sign into oss.jfrog.org for the first time.  After 
your initial log in, you will be provided a new password key.  You can view this password key by going to

https://oss.jfrog.org/artifactory/webapp/#/profile

Then clicking the Unlock button

## Copy of gradle.properties

Although the source code is open source, the user:password is not (to prevent everyone from being able to upload new 
snapshots).  A copy of the gradle.properties is hosted on the auto-services server in the polarizer folder
