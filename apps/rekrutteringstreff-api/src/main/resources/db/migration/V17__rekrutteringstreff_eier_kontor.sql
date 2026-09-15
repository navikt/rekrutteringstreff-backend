UPDATE rekrutteringstreff_eier e
SET kontor_enhetid = m.kontor_enhetid
FROM (VALUES
    -- Dev
    ('079939ac-fbf3-4d19-8299-4ee0c8886ddd'::uuid, '1124'),
    ('d3cc9524-4748-4516-bf76-fb75e84079b2'::uuid, '1124'),
    ('34c5f7aa-bc00-4457-bcf4-0b227d48ba9a'::uuid, '0403'),
    -- Prod
    ('a16e9ad3-7e03-4392-ab9b-9bdae430a444'::uuid, '0104'),
    ('15a901f9-2251-45da-9d80-52d68f3bf972'::uuid, '0101'),
    ('5c0d57ed-9527-4f89-acba-52b19c57a544'::uuid, '0137'),
    ('30e9a30d-e2ad-4097-b94b-7b57ce36c4d4'::uuid, '0106'),
    ('64ffdcf2-038b-4ecc-9478-b1c348dd400d'::uuid, '0106'),
    ('e283e771-a961-4dc7-9046-8e57c7a639a0'::uuid, '0106'),
    ('e4d26ab3-8d39-4e91-907e-0ced1264f0ae'::uuid, '0106'),
    ('04d7bd74-33ab-41d8-8727-ad6044fbea24'::uuid, '0105'),
    ('d03e4b2b-8ee3-4ec0-a606-4ef25e0c1d7f'::uuid, '0106'),
    ('b3c4253a-ef8a-4cc2-98ca-13a6c000def6'::uuid, '0502'),
    ('ac2a4d9d-d957-4918-ac00-ec962f1b1366'::uuid, '0105'),
    ('811f2375-47f5-4bfc-9590-17fdb5bfdb14'::uuid, '0105'),
    ('6bf84916-2eb1-4c92-b9eb-f06842e92616'::uuid, '0101'),
    ('9f1cce49-ac52-42ca-a2b9-98fd0dc2521b'::uuid, '0106'),
    ('b680680c-3ec8-4bd9-943d-cd1caab0b8cf'::uuid, '0105'),
    ('f9d2180d-da67-4591-bbdd-b7dcc4f7421f'::uuid, '0104'),
    ('546b512c-7922-43b9-8db2-a65488dc7b9c'::uuid, '0106'),
    ('3e9fa550-467c-426f-a8e6-40502842a60c'::uuid, '1208'),
    ('e879b4e8-6208-4cf5-80e1-f57fa5a60c5f'::uuid, '1208'),
    ('f56f5386-54c3-4d4f-b6e1-b2a2994300f2'::uuid, '1208'),
    ('fb4eb9b3-0942-48d2-b035-384281ab88ee'::uuid, '0704'),
    ('a101c09b-ef19-47ce-be2f-4f5faf623394'::uuid, '1161'),
    ('3c1cef3d-25eb-42a8-be99-e644529004e4'::uuid, '1161')
) AS m(id, kontor_enhetid)
WHERE e.id = m.id
  AND e.kontor_enhetid IS NULL;
