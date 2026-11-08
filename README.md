# Three-Kid Family Challenge
A Spring Boot service that receives person records one at a time, and after each record answers whether anyone stored
matches this pattern:
1. has a partner;
2. has exactly 3 children, and all 3 have that same partner as a parent;
3. at least one of those children is under 18.

Records may refer to parents, partners and children that have not been seen yet. Sending a record with a known ID
updates that person. The relations between people are kept bidirectional: if A lists B as a child, B gets A as a
parent.

Phase 1 of the assignment is implemented; phase 2 (deleting and ignoring IDs) is not.

## Requirements
- Java 25
- Maven is not needed: the Maven wrapper (`mvnw`) downloads the right version.

## Build, test and run
```shell
./mvnw test             # run all tests
./mvnw spring-boot:run  # start the service on http://localhost:8080
```
On Windows, use `mvnw.cmd` instead of `./mvnw`.

## API
`POST /api/v1/people` with one person record:
```json
{
  "id": 42,
  "name": "Ada Lovelace",
  "birthDate": "1815-12-10",
  "parent1": { "id": 1 },
  "parent2": { "id": 2 },
  "partner": { "id": 99 },
  "children": [{ "id": 77 }, { "id": 78 }]
}
```
Only `id` is required. A relation that is left out means the person has no such relation.

| Status | Meaning |
|---|---|
| `200 OK` | At least one person matches. The body is a list of all matching people, in the same format as the request. |
| `444 No Response` | Nobody matches. The body is empty. |
| `400 Bad Request` | The record is invalid: no ID, a reference without an ID, a person referring to themselves, or a third parent for a child. Nothing is stored. |

## Code structure
| Package | Contents |
|---|---|
| `controller` | `PersonController` (the endpoint) and `ApiExceptionHandler` (invalid records become `400`) |
| `dto` | `PersonDTO` and `PersonReferenceDTO`: the JSON format, with validation and conversion from and to `Person` |
| `service` | `PersonService`: stores the record and returns the matches, including the age check |
| `repository` | `PersonRepository`: in-memory storage, bidirectional relations and the list of potential matches |
| `domain` | `Person` and `InvalidPersonException` |

# Architecture and design decisions
## PersonRepository
The name `Repository` indicates data storage. As long as the public methods of this class stay the same, the way the
data is stored does not matter to the users of the class. This means we don't have to add persistent data storage yet,
but we can still do that later if needed.

`PersonRepository` keeps all people in memory, in a `HashMap` keyed by ID. Keeping the data in memory helps
performance: there are no network calls or conversions between objects and database rows, and looking up a person by
ID takes constant time.

The downsides are:
- the data is lost when the application is shut down;
- memory use grows with every new person, without a limit;
- the data cannot be shared between several running instances of the application.

One alternative would be to add a relational database. The data would survive a restart and could be queried, at the
cost of extra complexity in setup, configuration and testing.
Another alternative would be to persist all valid incoming requests in order, as an append-only log, and replay them
on startup. This also gives an audit trail of all changes, but adds complexity and increases startup time.

### One object per person
The repository keeps exactly one `Person` object per ID. When a record refers to someone we don't know yet, an
incomplete person (only the ID) is stored. When that person's own record arrives later, the stored object is updated
in place, instead of being replaced by a new object. All relations point to the stored objects, so whoever follows a
relation sees the latest data, including the birth dates the pattern needs.

An alternative is to replace the stored object on every update. This is simpler, but every person that refers to the
old object has to be pointed to the new one, and it is easy to miss one.

### Updates replace all relations (autorepair)
The assignment requires bidirectional integrity: if A says B is a child, B must list A as a parent. The repository
repairs this automatically: storing A also adds A to B's parents, and the same goes for parents and partners.

A record replaces all relations of that person, including relations that earlier records of other people established.
For example: child 77 says 42 is a parent, and later the record of 42 arrives without 77 in its children. Then the
relation between 42 and 77 is broken on both sides. The same goes for parents and the partner. When a person chooses
a new partner, the previous partner of both people is released. A relation that is left out of the JSON therefore
means "none", not "unknown".

An alternative is to only add relations, and never break relations that other records established. Then no
information is lost when a record is incomplete, but a person can never be released from an incorrect relation.

### Check first, then change
An update changes several people at once. If it could fail halfway, the data would be left half updated, and undoing
those changes is complex. So `addOrUpdate` first checks everything that can fail (a third parent), and only changes
data after that. A rejected record changes nothing.

### Potential matches
After each record, the service has to answer whether anyone stored matches the pattern. Instead of checking everyone
on every request, the repository keeps a list of potential matches: people who have a partner and exactly three
children who all have that partner as a parent. The list is updated during `addOrUpdate`, so finding the matches only
means reading it.

A person's status only depends on their partner, their children, and the parents of those children. So after an
update, only these people are checked again: the person in the record, their parents, children and partner from before
and after the update, and the person that the new partner leaves behind. Nobody else's status can change. Keeping the
list up to date therefore takes the same amount of work per request, no matter how many people are stored. Answering a
request does take longer as the number of potential matches grows, because each of them gets the age check and is
converted for the response; that cannot be avoided, because the response has to contain all matches.

The age check ("at least one child under 18") is not part of the potential matches. A child's age changes over time
without any new record arriving, so a stored result would go out of date. The age check is done on the potential
matches when a request is answered.

A child counts as under 18 until their eighteenth birthday. A child whose birth date we don't know yet does not count,
because we cannot tell whether they are under 18; the person can still match once that birth date arrives. The service
gets "today" from an injected `Clock` and reads it once per request, so all matches in one response are checked
against the same day, and tests can use a fixed date.

Alternatives:
- Check everyone on every request. This is simpler and always correct, but the work grows with the number of people
  stored, and the assignment evaluates performance under load.
- Only check the person in the record. This is cheap, but misses matches: a child's record can make their parents
  match, and a partner's record can make the other partner stop matching.

### Locking
All reads and writes go through a `ReentrantReadWriteLock`. An update changes several people, and nobody should see it
half done; the `HashSet`s in `Person` are not thread-safe either. A read/write lock lets several reads run at the same
time, while a write has the repository to itself. Because all access is under the lock, a plain `HashMap` is enough.

The downside is that writes are handled one at a time. Alternatives are a lock per person (more concurrency, but the
people in one update have to be locked in a fixed order to avoid deadlocks), or immutable `Person` objects that are
replaced as a whole (no locks for reading, but every update has to copy all related people).

### Command and query
Storing a record (`addOrUpdate`) and finding the matches (`findPotentialMatches`) are separate methods: one changes
data and returns nothing, the other returns data and changes nothing (command-query separation).

The stored `Person` objects must not be read outside the lock: another request could be changing them at the same
moment. So `findPotentialMatches` takes a filter and a mapper, and runs both under the read lock. The service passes
the age check as the filter and `PersonDTO::from` as the mapper, and only gets copies back. The repository does not
need to know about `PersonDTO`. `findPerson` works the same way: it takes a mapper and returns an `Optional` of the
copy.

Because the command and the query each take the lock separately, another request can be handled in between. The
response then reflects the state just after this record, possibly including that other request. This is accepted: the
assignment asks whether anyone matches *now*, and with requests arriving at the same time, a slightly later state is
just as valid an answer.

Alternatives:
- Let `addOrUpdate` return the matches, computed under the same write lock. The response then reflects exactly the
  state after this record, but the method both changes and returns data.
- Return an immutable copy type from the domain, such as a `PersonSnapshot` record. This works as well, but it is an
  extra type that mostly duplicates `PersonDTO`.
- Make the sets in `Person` thread-safe. This prevents exceptions, but a person could still be read halfway through an
  update.


## PersonServiceTest
`PersonServiceTest` is one scenario: a series of ordered unit tests that offer Person records to the same
`PersonService` one by one. Each test builds on the state left behind by the previous tests. The first record shows
that a record is stored and that the people it refers to are created as incomplete records; the next records show that
those records are completed, amended and changed, and so on.

This goes against the convention that unit tests are independent of each other. The reason is that it simplifies
setting up the system into a testable state: to test that a record amends an existing record, that existing record
has to be there first, and the earlier test already put it there. Every test therefore only needs to supply one new
record, and the series of tests reads as one story of how the data develops.

It is implemented in standard JUnit 5: `@TestInstance(Lifecycle.PER_CLASS)` lets all tests share one test instance
(and with it the service and repository), and `@TestMethodOrder(OrderAnnotation.class)` with `@Order` sets the order.
No Spring context is needed, because `PersonService` and `PersonRepository` can be created directly.

The downsides are:
- a single test cannot be run on its own, because it depends on the tests before it;
- when one test fails, the tests after it will probably fail as well, so the first failure is the one to look at;
- inserting a step in the middle may change the state that later steps rely on.

One alternative is to make every test independent, and set up the required state in each test, possibly through
shared helper methods or fixtures. This is the conventional approach and keeps failures isolated, but every test needs
to repeat (part of) the setup, and the setup grows with every step in the scenario.
Another alternative is a single `@TestFactory` that returns a stream of `DynamicTest`s. This keeps the whole scenario
in one method, but `@BeforeEach` and `@AfterEach` do not run per step, and the steps cannot be selected individually
in the IDE.

## Person
### Relations as sets
`Person` uses `Set`s for parents and children, because these should be unique. A person can be referred to by an
incomplete record (only the ID) and later by a complete record, and both should count as the same person. That is why
`equals` and `hashCode` only use the `id`. They do not use the relations: those point back to each other (a parent
refers to the child, the child to the parent), which would cause endless recursion. The partner is a single reference.

Alternatives:
- A `List` with explicit duplicate checks. This gives the same result with more code.
- Two fields `parent1` and `parent2`, as in the API. This suggests an order that has no meaning, and checking for
  duplicates and free places is more awkward.
- Store relations as IDs instead of object references, and look people up in the repository when needed. This avoids
  outdated incomplete objects and recursion when converting to JSON, at the cost of a lookup for every relation.

### Invalid records
Every person record must have an ID; without it, the record cannot be stored or referred to. `PersonDTO` rejects a
record without an ID with an `InvalidPersonException`, and the API answers with `400 Bad Request`. The check is in the
constructor of `PersonDTO`, so an invalid `PersonDTO` can never exist.

The same goes for references to other people. A relation may be left out of the JSON (or be `null`), which means the
person has no such relation (see "Updates replace all relations"). But a reference that is present must have an ID:
`"parent1": {}`, `"parent1": { "id": null }` and a `null` entry in the list of children are rejected with
`400 Bad Request`. Ignoring them instead would hide an error in the request.

A person cannot be their own parent, partner or child either. A record that refers to its own ID in one of those
relations is rejected with `400 Bad Request` as well.

An alternative is Bean Validation (`@NotNull` with `@Valid` on the controller). This is the standard Spring approach
and gives detailed error messages, but needs an extra dependency, and the self-reference check would still need custom
code.

### Third parent
If a child (Person A) already has two parents (Person B and Person C), and a record arrives that adds a third parent
(Person D), an `InvalidPersonException` is thrown, and the API answers with `400 Bad Request`. There is no way to
determine which one of the existing parents should be replaced, so the request is considered incorrect. The whole
record is rejected, and nothing is changed (see "Check first, then change").

An alternative is "last record wins": replace one of the existing parents with the new one. This never rejects a
request, but there is no rule for which parent to replace, and data would be lost without notice.

## Response format
The assignment says: "The response should consist of all the people currently matching the pattern. We should respond
with everything we know about the matching people (you might not know their name or birthdate yet)."

The response is a list with one record per matching person, in the same format as the request (`PersonDTO`). Each
record contains everything we know about that person: ID, name, birth date, parents, partner and children. Related
people are given as ID references (`{ "id": 77 }`), not as full records. A value we don't know yet is `null`, and a
person without known children has an empty list. Within each record, parents and children are sorted by ID, so the
same person always looks the same. The order of the matching people in the list has no meaning.

The stored `Person` objects are converted with `PersonDTO.from` (under the repository's read lock, see "Command and
query"), instead of being returned directly.
This keeps the API format separate from the internal model, and avoids endless recursion when converting to JSON:
a parent refers to the child, and the child refers back to the parent.

Alternatives:
- Return related people as full records. This cannot be done completely, because the relations point back to each
  other, so it would have to stop somewhere.
- Expand one level: the matching person with the partner and children as full records, whose own relations are given
  as references. This gives more information in one response, but needs a separate response format, and the
  assignment does not clearly ask for it.
- Return `Person` directly. This needs no conversion, but makes the internal model part of the API, and needs Jackson
  annotations to stop the recursion.
