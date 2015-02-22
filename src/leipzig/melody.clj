(ns leipzig.melody
  (:require [leipzig.scale :as scale]))

(defn bpm
  "Returns a function that translates a beat number into seconds.
  e.g. ((bpm 90) 5)" 
  [beats] (fn [beat] (-> beat (/ beats) (* 60))))

(defn having
  "Zips an arbitrary quality onto a melody.
  e.g. (->> (rhythm [1 1/2]) (having :drum [:kick :snare]))"
  [k values notes]
  (map #(assoc %1 k %2) notes values))

(defprotocol Utterable (utter [thing time duration velocity]))

(extend-protocol Utterable
  Object
  (utter [pitch time duration velocity]
    [{:pitch pitch :time time :duration duration :velocity velocity}])
  
  clojure.lang.Sequential
  (utter [cluster time duration velocity]
    (mapcat #(utter % time duration velocity) cluster))

  clojure.lang.MapEquivalence
  (utter [chord time duration velocity]
    (utter (-> chord vals sort) time duration velocity))

  nil
  (utter [pitch time duration velocity]
    [{:time time :duration duration}]))

(def is
  "Synonym for constantly.
  e.g. (->> notes (wherever (comp not :part), :part (is :bass)))"
  constantly)

(defn- if-applicable [applies? f] (fn [x] (if (applies? x) (f x) x)))
(defn wherever
  "Applies f to the k key of each note wherever condition? returns true.
  e.g. (->> notes (wherever (comp not :part), :part (is :piano))"
  [applies? k f notes]
  (map
    (if-applicable applies? #(update-in % [k] f))
    notes))

(defn where
  "Applies f to the k key of each note in notes, ignoring missing keys.
  e.g. (->> notes (where :time (bpm 90)))"
  [k f notes]
  (wherever #(contains? % k), k f notes))

(defn all
  "Sets a constant value for each note of a melody.
  e.g. (->> notes (all :part :drum))"
  [k v notes]
  (wherever (is true), k (is v) notes))

(defn after
  "Delay notes by wait.
  e.g. (->> melody (after 3))"
  [wait notes] (where :time (scale/from wait) notes))

(defn- before? [a b] (<= (:time a) (:time b)))
(defn with
  "Blends melodies.
  e.g. (->> melody (with bass drums))"
  ([[a & other-as :as as] [b & other-bs :as bs]]
   (cond
     (empty? as) bs
     (empty? bs) as
     (before? a b) (cons a (lazy-seq (with other-as bs)))
     :otherwise    (cons b (lazy-seq (with as other-bs)))))
  ([as bs & others] (reduce with (cons as (cons bs others)))))

(defn but
  "Replaces part of a melody with another.
  e.g. (->> notes (but 2 4 variation))"
  [start end variation notes]
  (let [starts-in? (fn [{:keys [time]}]
                    (and (<= start time) (< time end)))
        enters (fn [{:keys [time duration]}]
                 (< start (+ time duration)))
        clip (fn [{:keys [time] :as note}]
               (if (enters note)
                 (assoc note :duration (- start time))
                 note))]
    (->> notes
         (filter (complement starts-in?))
         (map clip)
         (with (after start variation)) )))

(defn duration
  "Returns the total duration of notes.
  e.g. (->> melody duration)"
  [notes]
  (->> notes (map (fn [{:keys [time duration]}] (+ time duration))) (cons 0) (apply max)))

(defn then 
  "Sequences later after earlier.
  e.g. (->> call (then response))"
  [later earlier]
  (->> later
       (after (duration earlier))
       (with earlier)))

(defn mapthen [f & melodies]
  "Apply f to each melody, then join them together.
  e.g. (mapthen drop-last [bassline vocals])"
  (->> melodies
       (apply map f)
       (reduce #(then %2 %1))))

(defn times
  "Repeats notes n times.
  e.g. (->> bassline (times 4))"
  [n notes]
  (->> notes
       (repeat n)
       (mapthen identity)))

(defn- render [duration pitch velocity]
  (if (sequential? duration)
    (mapthen #(render % pitch velocity) duration)
    (utter pitch 0 duration velocity)))

(defn phrase
  "Translates a sequence of durations and pitches into a melody.
  nil pitches signify rests, vectors represent clusters, and maps
  represent chords. Vector durations represent repeated notes.
  e.g. (phrase [1/2 1/2 3/2 3/2] [0 1 nil 4])
  (phrase [1 1 2] [4 3 [0 2]])
  (phrase [1 [1 2]] [4 3])
  (phrase (repeat 4) (map #(-> triad (root %))) [0 3 4 3])"
  ([durations pitches velocities]
   (->> (map render durations pitches velocities)
        (reduce #(then %2 %1) [])))
  ([durations pitches]
   (->> (phrase durations pitches (repeat 1.0))
        (map #(dissoc % :velocity)))))

(defn rhythm
  "Translates a sequence of durations into a rhythm.
  e.g. (rhythm [1 1 2])"
  [durations]
  (phrase durations (repeat nil)))
