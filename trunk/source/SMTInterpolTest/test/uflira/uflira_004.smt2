(set-option :produce-proofs true)
(set-logic QF_UFLIRA)
(declare-fun x () Int)
(declare-fun y () Int)
(declare-fun z () Real)
(assert (<= (+ (* (- 3) x) (* 3 y) z) (- 1)))
(assert (<= (+ (* 3 x) (* (- 3) y) z) 2))
(assert (= z 0.0))
(check-sat)
;(get-model)
(set-option :print-terms-cse false)
(get-proof)
