SELECT ccs.TICKER, ccs.QTY, ccs.FACE_VALUE/100 AS PRICE, ccs.ISSUER_KEY
FROM vault_states AS vs JOIN contract_share_states AS ccs
ON vs.transaction_id = ccs.transaction_id
WHERE vs.output_index = ccs.output_index
AND ccs.TRANSACTION_ID = '$tx'