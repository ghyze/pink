

# Architecture and design decisions
## PersonRepository
The name `Repository` indicates data storage. As long as the public methods of this class stay the same, the way the
data is stored does not matter to the users of the class. This means we don't have to add persistent data storage yet,
but we can still do that later if needed.

`PersonRepository` keeps all people in memory, in a `ConcurrentHashMap` keyed by ID. A `ConcurrentHashMap` is used
instead of a `HashMap` because requests are handled on several threads at once. Keeping the data in memory also helps
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
