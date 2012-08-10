(ns nimrod.internal.switch
  (:import [java.util.concurrent.atomic AtomicInteger]))

(defonce switches {:requests (AtomicInteger.)})

(defmacro with-switch [type threshold body failure]
  `(let [~'counter (switches ~type) ~'count (.incrementAndGet ~'counter)]
    (try
      (if (<= ~'count ~threshold)
        (do ~body)
        (do ~failure))
      (finally 
        (.decrementAndGet ~'counter)))))
