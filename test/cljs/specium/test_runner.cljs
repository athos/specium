(ns specium.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            specium.core-test))

(doo-tests 'specium.core-test)
