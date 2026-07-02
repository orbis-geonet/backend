# Orbis backend architecture overview #

<!-- toc -->

* [Packages and layers](#packages-and-layers)
  * [org.springframework.data.mongodb.core.aggregation](#orgspringframeworkdatamongodbcoreaggregation)
  * [to.orbis.v2.backend](#toorbisv2backend)
* [Reactive programming](#reactive-programming)
* [CQRS pattern](#cqrs-pattern)
* [Lombok usage](#lombok-usage)
* [Controllers](#controllers)
* [Services](#services)
* [Repositories](#repositories)
* [Entities, DTOs, Request objects and data mappers](#entities-dtos-request-objects-and-data-mappers)
* [Mappers](#mappers)
* [Notifications](#notifications)
  * [Notification workflow](#notification-workflow)
* [Places checkin rules](#places-checkin-rules)
* [Data storage: Mongo, RTDB, Firestore, Cloud buckets](#data-storage-mongo-rtdb-firestore-cloud-buckets)
  * [Mongo](#mongo)
  * [RTDB](#rtdb)
  * [Firestore](#firestore)
  * [Cloud buckets](#cloud-buckets)

<!-- tocstop -->

# Packages and layers

## org.springframework.data.mongodb.core.aggregation 
Some utility functions to add missing functionality to how spring supports aggregation framework in mongo.
Adds 2 aggregation operations - FreeFormOperation, which allows to almost directly translate any mongo aggregations operation into spring wrapper compatible 
with MongoTemplate, and PipelineLookupOperation adds second form of Lookup operation which is not based on field-to-field correspondence, but allows
outlining custom pipleline as described in [Mongo documentation](https://www.mongodb.com/docs/manual/reference/operator/aggregation/lookup/#join-conditions-and-subqueries-on-a-joined-collection).

This package was added as Spring did not support needed functionality at the time of writing, or it was easier to add a couple of tweaks to make it easier to use.

## to.orbis.v2.backend
Main package of the application. Serves as a root of hierarchy and contain only one class `BackendApplication` itself

# Reactive programming

This project uses spring-webflux which is spring implementation of reactive programming. Reactive programming decoratively
defines what should happen with input data to get an output data each of the controllers' methods should return Mono which results
in a ResponseEntity of corresponding type, or type itself. Mono internally represents processing pipeline which is based on 
`project-reactor`. Advantage of this approach is that execution environment manages stages of the pipeline in the way to
avoid blocking any threads. E.g. if some Mongo response is needed to build controller's response instead of waiting for 
said response project-reactor send mongo query and places a hook which signals when data are ready in mongo. In turn 
project reactor wakes up corresponding Mono and continues processing sending the result to the client. 

This allows to reduce number of threads serving comparable number of requests per second and avoid spending time and CPU
power on blocked threads. 

More details regarding project-reactor could be found on [project-reactor's site](https://projectreactor.io/docs/core/release/reference/)

More details regarding spring-webflux are available on [spring-weblux's site](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)

General overview of what was described before in [more details](https://www.youtube.com/watch?v=M3jNn3HMeWg). 

# CQRS pattern

This project relies in part on [CQRS pattern](https://martinfowler.com/bliki/CQRS.html) at least under the hood. Client gets responses
in models from `to.orbis.v2.backend.models.dto` package and updates and creates of new objects are mediated by models in 
`to.orbis.v2.backend.models.requests`. This helps to impose different limitations for creating and editing objects and 
helps to decouple dto's and requests in general

# Lombok usage

This project uses lombok with whole-project field defaults as final and private. So any time
```java
public class ExampleClass {
    int someField;
}
```
is written lombok converts this into 
```java
public class ExampleClass {
    private final int someField;
}
```

For almost all the objects - services, controllers, mappers, configurations, repositories, you name it this default settings
makes a lot of sense. These objects are singletons and if fields need to be assigned outside the constructor - it has state 
and that's sure sign something is wrong.

For models, DTOs, and perhaps requests this need to be overridden. So defaults for `to.orbis.v2.backend.models` removes 
final from the fields defaults.

More information of what lombok can do for you can be found on [official site](https://projectlombok.org/features/all)

Also `val` is used all over the place as part of type inference and to encourage immutability as much as possible.

# Controllers

Controllers live in `to.orbis.v2.backend.controllers`. Controllers are a regular `@RestController`s which can use `@Validated` 
and `@PreAuthorize` annotations to control validation and security. At the moment only `"isAuthenticated"` is used as 
`@PreAuthorize` parameter providing distinction between authenticated and non-authenticated users. User id can be 
injected in controller's method by using `Principal` as a parameter.

Responsibility of a controller is to accept and validate any data and prepare data for services to use. No *DTO or *Request objects
are allowed beyond controller's layer.

# Services

Services live in `to.orbis.v2.backend.services` and are core of the application doing actual work of supporting domain logic.
They are called by controllers (most of the time) and use repositories layer to access mongo database. Generally what 
service's responsibility is quite clear from service's name.

More details in each individual services.

# Repositories

Repositories are the interface to Mongo and two main flavors of those are used in the project. `*Repository` is an interface
extended from `ReactiveMongoRepository<ID, Type>` and provide simple CRUD and generated queries of a `spring-data` flavor.

More about how they work could be found [here](https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/#repositories)
there will be some reactive/non-reactive differences, but generally the idea is the same.

Second flavor is `*AggregationsRepository` - initially they were added to support queries with [aggregations framework](https://www.mongodb.com/docs/manual/core/aggregation-pipeline/)
and to do this they've got `ReactiveMongoTemplate` injected. Later anything needing `ReactiveMongoTemplate` just went into
corresponding `*AggregationsRepository`.

# Entities, DTOs, Request objects and data mappers

Basically there are no true entities, just DTO's of Controllers versus Service layers. All of them are situated in `to.orbis.v2.backend.models`
this package contains some top-level common enums, and some classes which do not really belong to any particular package.

`dto` package contains DTOs which are sent to the client as an answer to client's queries/requests

`entity` package contains models which correspond to Mongo documents. As a rule of thumb Classes which have modifiers in their names
like `ExtendedPost` are NOT intended to be saved in Mongo and are used to represent aggregations' response objects. There
are no way to say if entity is supposed to be saved to Mongo other than checking if `.save()` is ever called with it as a
parameter.

`requests` package contains DTOs intended to modify database e.g. `CreatePostRequest` and so on. They usually have some 
kind of validation annotations on them.

`ig` - related to instagram

`firebase` - firebase models

# Mappers

To convert between DTOs, Entities, and Requests MapStruct is used. Mappers' definitions are located in `to.orbis.v2.backend.mappers`.

More about how to use this library is available on [official site](https://mapstruct.org/documentation/stable/reference/html/).

If new fields are added to DTOs/Entities/Requests warning could appear during compilation.

```text
warning: Unmapped target property: "storiesHidden".
    Group createRequestToGroup(CreateGroupRequest groupRequest);
```

Pay attention as it may lead to a hard to catch bugs when some fields are not populated, and it is not clear why.

# Notifications

`to.orbis.v2.backend.services.NotificationsService` is a main class taking care of all the notifications. When new notification
need to be sent it is saved to `notifications` collection which allows to see how many unread notifications there are, 
check old notifications and do other UI notifications maintenance. Besides, if user agreed to receive push notifications
and registered one or more devices `NotificationsService` also takes care of preparing messages and sending them to users
using `FirebaseMessaging`.

## Notification workflow
1. client service initiates notification
2. NotificationsService finds corresponding template in `resources/messageTemplates` or uses fallback String.format calls if there are no template available
3. NotificationService prepares all necessary details - subject, text from template, from, to and other metadata
4. NotificationService finds all fcmTokens to which messages needs to be pushed
5. NotificationService delegates sending actual messages to `FirebaseMessaging` on a separate thread pool
6. NotificationService saves notifications one per user to the mongo collection
7. done

# Places checkin rules

Each place if nothing happening shrinks from any size to 500m during 24h from last change and then from 500m to 0 (not displayed)
during next year. Given this current size of any place can be calculated from lastChangeTimestamp and lastSize.

Exact algorithm for calculation can be found in `to.orbis.v2.backend.models.entity.Place.currentSize` method.

Non-standard change happens when someone checks-in to the place, or neighbouring place grows bigger and starts to intersect with
a current's place circle of currentSize. Basically if someone goes into given places - place grows, if people are going to 
nearby places they grow and push given place's borders, so it shrinks.

Unlike standard time-related changes non-standard change needs to be communicated to all interested user devices so 
map on user devices is dynamic. This is done using [RTDB](#RTDB)

Actual code making decisions regarding notifications and so can be found in `CheckinService`

`to.orbis.v2.backend.services.CheckinService.checkin` - loads all required data and checks if this particular checkin needs
to be taken into account (e.g. person can't checkin twice into the same place during 24h)

`to.orbis.v2.backend.services.CheckinService.doCheckin` - actually performs a checkin and makes decisions about new size, 
shrinking neighbours and sending RTDB updates.

`to.orbis.v2.backend.models.entity.Place.checkin` - calculates new size of a place to which checkin happens

`to.orbis.v2.backend.services.CheckinService.updateTouchedSizes` - updates sizes of touched places

# Data storage: Mongo, RTDB, Firestore, Cloud buckets

## Mongo

Main data storage of the application. Contains all relevant info regarding places, groups, users and supplementary information

Does not contain chats and chat messages

Does not contain user media like pictures, videos, group pictures, and so on.

## RTDB

Contains realtime information about group ownership, place sizes and thumbnail generation. To reduce amount of traffic only non-standard
place size updates get published into RTDB. See details in [Places checkin rules](#places-checkin-rules)

## Firestore

Firestore takes care of chats and chat messages. To provide push notifications one of the running servers assumes the role
of chat messages notifier and subscribes to Firestore updates. When new message gets added this server uses `NotificationService`
to send push notification about the chat. 

More details available in `to.orbis.v2.backend.services.FirestoreService.initListenForChatMessages`

## Cloud buckets

Cloud buckets are used to store users' media - user pics, shared photos and video and such stuff.

Resizer cloud function generates thumbnails for all media uploaded to the bucket.

Default firestore bucket is used for this.
