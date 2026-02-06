(ns exercise-timer.format-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [exercise-timer.format :as format]))

;; ============================================================================
;; Unit Tests for Time Formatting (Task 9.1)
;; ============================================================================

(deftest test-seconds-to-mm-ss-zero
  (testing "seconds-to-mm-ss formats zero seconds"
    (is (= "00:00" (format/seconds-to-mm-ss 0)))))

(deftest test-seconds-to-mm-ss-single-digit-seconds
  (testing "seconds-to-mm-ss zero-pads single digit seconds"
    (is (= "00:05" (format/seconds-to-mm-ss 5)))
    (is (= "00:09" (format/seconds-to-mm-ss 9)))))

(deftest test-seconds-to-mm-ss-double-digit-seconds
  (testing "seconds-to-mm-ss handles double digit seconds"
    (is (= "00:10" (format/seconds-to-mm-ss 10)))
    (is (= "00:59" (format/seconds-to-mm-ss 59)))))

(deftest test-seconds-to-mm-ss-one-minute
  (testing "seconds-to-mm-ss formats exactly one minute"
    (is (= "01:00" (format/seconds-to-mm-ss 60)))))

(deftest test-seconds-to-mm-ss-minutes-and-seconds
  (testing "seconds-to-mm-ss formats minutes and seconds"
    (is (= "01:05" (format/seconds-to-mm-ss 65)))
    (is (= "05:30" (format/seconds-to-mm-ss 330)))
    (is (= "12:45" (format/seconds-to-mm-ss 765)))))

(deftest test-seconds-to-mm-ss-large-values
  (testing "seconds-to-mm-ss handles large minute values"
    (is (= "61:01" (format/seconds-to-mm-ss 3661)))
    (is (= "99:59" (format/seconds-to-mm-ss 5999)))))

(deftest test-seconds-to-mm-ss-five-minutes
  (testing "seconds-to-mm-ss formats 5 minutes (default session)"
    (is (= "05:00" (format/seconds-to-mm-ss 300)))))

;; ============================================================================
;; Property-Based Tests for Time Formatting (Task 9.2)
;; ============================================================================

;; Generator for non-negative seconds (0 to 7200 = 2 hours)
(def gen-seconds
  "Generator for non-negative seconds (0 to 7200)"
  (gen/choose 0 7200))

;; Property 10: Time Format Validation
;; **Validates: Requirements 4.5**
(defspec ^{:feature "exercise-timer-app"
           :property 10
           :description "Time Format Validation"}
  time-format-validation-property
  100
  (prop/for-all [seconds gen-seconds]
    (let [formatted (format/seconds-to-mm-ss seconds)]
      (and
       ;; Should be a string
       (string? formatted)
       ;; Should match MM:SS pattern (MM can be 2+ digits)
       (re-matches #"\d{2,}:\d{2}" formatted)
       ;; Should contain a colon
       (clojure.string/includes? formatted ":")
       ;; Minutes and seconds should be valid
       (let [parts (clojure.string/split formatted #":")
             minutes (js/parseInt (first parts) 10)
             secs (js/parseInt (second parts) 10)]
         (and
          ;; Seconds should be 0-59
          (>= secs 0)
          (< secs 60)
          ;; Seconds should be zero-padded (2 digits)
          (= 2 (count (second parts)))
          ;; Minutes should match calculation
          (= minutes (quot seconds 60))
          ;; Seconds should match calculation
          (= secs (rem seconds 60))))))))

;; Property: Round-trip conversion
(defspec time-format-round-trip-property
  100
  (prop/for-all [seconds gen-seconds]
    (let [formatted (format/seconds-to-mm-ss seconds)
          parts (clojure.string/split formatted #":")
          minutes (js/parseInt (first parts) 10)
          secs (js/parseInt (second parts) 10)
          reconstructed (+ (* minutes 60) secs)]
      ;; Reconstructed seconds should equal original
      (= seconds reconstructed))))
