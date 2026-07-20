# App enging settings #

This folder contains app engine settings

- app.yaml - settings template and the only checked-in file. To be able to deploy from local development machine this file need to be copied to 
- app-prod.yaml - prod settings or
- app-staging.yaml - staging settings
  ./gradlew clean build deployStaging -Dorg.gradle.java.home="/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home" --stacktrace

And env variables and other app engine settings can be tailored to the target environment.

NB: Please don't commit environment specific app*.yaml files as they contain passwords and all

Reference for file format could be found [here](https://cloud.google.com/appengine/docs/standard/java-gen2/config/appref)
