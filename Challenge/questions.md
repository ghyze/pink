\# Error handling

* What should we do when the input is incorrect? for example when id is missing or corrupted.
* What should we do when the input document is correct but causes data corruption? For example, when a person has two parents and an unrelated third person claims to be the parent.
* Does birthDate need to be taken into account? For example, the parent could have a birthDate after the child, or the gap could be too small or too big.

In these cases, a general HTTP-400 response (Bad Request) would make sense.



\# Input format

* Are IDs always positive integers?
* What is the maximum ID that I can expect?



\# Response

* Do we match only the ID of the requested person, or also the parent IDs, partner ID and children IDs?
* "After each record arrives it must decide, immediately, whether \*\*any\*\* person now stored satisfies this pattern". Does this mean searching the entire database, or only the relevant records?
* It would seem that the required response is to expand the IDs in the request to full persons, if the requested person matches the pattern. Otherwise, there should be no response (HTTP 444). This means only the person in the request, and not their relations or unrelated people, is evaluated.



\# Other questions

* How many records (not requests) can be expected?
* Do the records have to be saved to persistent storage, so they survive service restarts?





