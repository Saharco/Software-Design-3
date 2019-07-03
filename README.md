# CourseApp: Assignment 3

## Authors
* Sahar Cohen, 206824088
* Yuval Nahon, 206866832

### Previous assignment
This assignment uses the code from the submission by: 206824088-206866832

## Library selection
This submission uses the library identified by the number: 4

## Notes

### Implementation Summary
We picked this library for two reasons: First, the primary class in the library (the `Database` module), provides an abstraction on top of `SecureStorage` that is relatively similiar to ours (fluent API, seems to be inspired by Firebase Firestore as well). Second, this library supports caching of recently written elements into the database - we think this is a mandatory mechanism for this project, otherwise the time limits wonuldn't be achieved.

Our implementation also offers an abstraction on top of the `Database` abstraction (abstract-ception!) in the form of the `DatabaseAbstraction` class, which offers a concise and less verbose API for very common operations and makes use of our `ObjectSerializer` to offer support for reading & writing documents of any serliazible type, such as maps.

The most experimental thing we've done in our implementation was implementing all of the bot operations **locally on the RAM** (aka "Fake"), and then refactoring that to work with `CompletableFuture` and the library abstraction to achieve the complete implementation. We think this attempt was nice, but could've been excecuted better. Also, when implementing the real thing, we made sure that the code **always** runs (on all branches except feature branches that branch from the "develop" branch - we worked with gitflow) so it was very easy to test methods and callbacks one at a time. The bots have 6 callbacks in total each: `lastMessageCallback, calculatorCallback, keywordTrackingCallback, messageCounterCallback, tippingCallback, surveyCallback` - these are created as annonymous classes inside the creator methods in `CourseBotsImpl` and are removed & re-attached when a bot is fetched. Finally, the `KeywordsTracker` wrapper class contains all the logic for keywords tracking (regex / messages' media types) and overloads the set/get operators for very convenient, map-like usage.

The only changes we had to do in the provided library were two bug fixes: 1) it seems like the Database::update method was broken, so we provided a very simple fix. 2) we deleted the CourseAppAPI class, because its existance kept opening unnecssary `SeucreStorage` instances and is coupled with CourseApp - which is not needed. Other than that - the library stayed exactly the same. Additionally, The implementation does **not** use .join() / .get() at all, except for tests.

### Testing Summary
In total, we have 29 tests and achieve nearly 100% test coverege for the newly added classes. The tests run on the JUnit environment and use MockK. We used our implementation for CourseApp from the previous assignment as a **fake** to test the implementation of the bots. We tried to think of as many edge cases as we could, such as maliciously tipping negative amounts - we hope that we covered most of them.

### Difficulties
Our main difficulty came from many misundertandings of the provided library. For example, it took us a very long time to realize that Database::delete, which takes a document and some fields as a parameter, actually deletes **document** and retrieves the **fields**. We don't understand this design choice, but if it works: don't fix it. In this case, for example, we found the Database::remove method that suited our needs. Configurating Guice in this assignment was rather easy because all we needed to do was bind the implementaions of the CourseBot and CourseBots to the interfaces. In the tests, we needed to add more Guice models and in particular have our CourseApp fake injected as an **eager singleton** - this bug took us a while to find out.

### Feedback
Like the previous assignment, we both feel that the specifications could've been a lot more detailed in the .pdf and the documentation, but we do understand the large amount of edge cases that this assignemnt entailed. Other than that - it was a very fun assignment. We think that the final assignment format (using another library) should be preserved for future semesters - we've learned a lot.
