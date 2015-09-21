# onyx-dashboard

A dashboard for the [Onyx](https://github.com/onyx-platform/onyx) distributed computation system, version 0.7.5.3-SNAPSHOT.

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/MichaelDrogalis/onyx?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Circle CI](https://circleci.com/gh/onyx-platform/onyx-dashboard.svg?style=svg)]

## Design and User Guide

A description and user guide disguised as a blog post can be found [here](http://lbradstreet.github.io/clojure/onyx/distributed-systems/2015/02/18/onyx-dashboard.html).

## Versioning

Version numbers will kept in sync with [Onyx]
(https://github.com/onyx-platform/onyx). For example, to use a version
compatible with Onyx 0.7.5.3-SNAPSHOT, use a version of the dashboard beginning with
0.7.5.3-SNAPSHOT. The fourth version number will be reserved for dashboard versioning, in
order to provide releases out of band with Onyx.

## Development

Setup an environment variable for `PEER_CONFIG` in your lein user profiles.clj like so:

```clojure
{:user {:env {:peer-config "path-to-peerconfig.edn"}}}
```

or by setting the environment variable in your shell.

Then open a terminal and run `lein figwheel` to start the Figwheel build
(https://github.com/bhauman/lein-figwheel).

and run `lein repl` to start your repl.

In the REPL, run

```clojure
(user/go)
```

Then point your browser at http://localhost:3000/

## Deployment

Configure an environment variable `PEER_CONFIG`.

e.g. `PEER_CONFIG="peer-config.edn"` 

Where peer-config.edn contains the peer-config used by your peers e.g.

```
{:zookeeper/address "127.0.0.1:2188"
 :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
 :onyx.messaging/impl :aeron
 :onyx.messaging/peer-port-range [40200 40260]
 :onyx.messaging/bind-addr "localhost"}
```

Then run the Onyx Dashboard in one of several ways:

* Download the [uberjar
version](https://github.com/lbradstreet/onyx-dashboard/releases).
  Then run it via `java -jar FILENAME`.
* Or build and run the uberjar `lein clean && lein with-profile uberjar uberjar`. 
Then run it via `java -jar target/onyx-dashboard.jar`.
* Or use the development instructions above.

## License

Copyright © 2015 Lucas Bradstreet & Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.7.0-SNAPSHOT (ecadc3ce).
