# specium
[![Clojars Project](https://img.shields.io/clojars/v/specium.svg)](https://clojars.org/specium)
[![CircleCI](https://circleci.com/gh/athos/specium/tree/master.svg?style=shield)](https://circleci.com/gh/athos/specium/tree/master)
[![codecov](https://codecov.io/gh/athos/specium/branch/master/graph/badge.svg)](https://codecov.io/gh/athos/specium)

Specium provides the inverse function of `clojure.spec(.alpha)/form`, reducing `eval` calls as much as possible.

## Installation

Add the following to your `:dependencies`:

[![Clojars Project](https://clojars.org/specium/latest-version.svg)](http://clojars.org/specium)

## Usage

A common way of getting a `s/form`ed spec form back into a spec object is to call `eval` with it:

```clj
=> (s/form (s/map-of keyword? integer?))
(clojure.spec.alpha/map-of clojure.core/keyword? clojure.core/integer?)
=> (eval *1)
#object[clojure.spec.alpha$every_impl$reify__946 0x6d47d0b2 "clojure.spec.alpha$every_impl$reify__946@6d47d0b2"]
=> (s/conform *1 {:key "wrong value"})
:clojure.spec.alpha/invalid
=> (s/explain *2 {:key "wrong value"})
In: [:key 1] val: "wrong value" fails at: [1] predicate: integer?
:clojure.spec.alpha/spec  #object[clojure.spec.alpha$every_impl$reify__946 0x6d47d0b2 "clojure.spec.alpha$every_impl$reify__946@6d47d0b2"]
:clojure.spec.alpha/value  {:key "wrong value"}
nil
=> 
```

Though it perfectly works, in some cases you might not be able to overlook `eval`'s inefficiency (eg. where lots of spec forms have to be dealt with).

_Specium_ provides the `->spec` fn, which can be used as the inverse function of `s/form`; that is, you can use it to get spec forms back to spec objects efficiently, almost without calling `eval`:

```clj
=> (require '[specium.core :as specium])
nil
=> (s/form (s/map-of keyword? integer?))
(clojure.spec.alpha/map-of clojure.core/keyword? clojure.core/integer?)
=> (def s (specium/->spec *1))
#'user/s
=> (s/conform s {:key "wrong value"})
:clojure.spec.alpha/invalid
=> (s/explain s {:key "wrong value"})
In: [:key 1] val: "wrong value" fails at: [1] predicate: integer?
:clojure.spec.alpha/spec  #object[clojure.spec.alpha$every_impl$reify__946 0x581fa0b "clojure.spec.alpha$every_impl$reify__946@581fa0b"]
:clojure.spec.alpha/value  {:key "wrong value"}
nil
=>
```

Note that we said "**almost** without calling `eval`", which means that `eval` is actually necessary in some cases, especially in the case the spec form has an `fn` form in it as a predicate.

## License

Copyright © 2017 Shogo Ohta

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
