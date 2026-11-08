

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

### Locking
All reads and writes go through a `ReentrantReadWriteLock`. An update changes several people, and nobody should see it
half done; the `HashSet`s in `Person` are not thread-safe either. A read/write lock lets several reads run at the same
time, while a write has the repository to itself. Because all access is under the lock, a plain `HashMap` is enough.

The downside is that writes are handled one at a time. Alternatives are a lock per person (more concurrency, but the
people in one update have to be locked in a fixed order to avoid deadlocks), or immutable `Person` objects that are
replaced as a whole (no locks for reading, but every update has to copy all related people).


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

### Missing ID
Every person record must have an ID; without it, the record cannot be stored or referred to. `PersonDTO` rejects a
record without an ID with an `InvalidPersonException`, and the API answers with `400 Bad Request`. The check is in the
constructor of `PersonDTO`, so an invalid `PersonDTO` can never exist.

The same goes for references to other people. A relation may be left out of the JSON (or be `null`), which means it
is not known. But a reference that is present must have an ID: `"parent1": {}`, `"parent1": { "id": null }` and a
`null` entry in the list of children are rejected with `400 Bad Request`. Ignoring them instead would hide an error in
the request.

A person cannot be their own parent, partner or child either. A record that refers to its own ID in one of those
relations is rejected with `400 Bad Request` as well.

An alternative is Bean Validation (`@NotNull` with `@Valid` on the controller). This is the standard Spring approach
and gives detailed error messages, but needs an extra dependency for a single check.

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
person without known children has an empty list. Parents and children are sorted by ID, so the same data always gives
the same response.

The controller converts the stored `Person` objects with `PersonDTO.from`, instead of returning `Person` directly.
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
