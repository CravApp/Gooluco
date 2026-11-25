-- DATOS DE DEMOSTRACIÓN 100 % FICTICIOS. No corresponden a ninguna persona real.
-- Se cargan solo en local/pruebas: npm run db:seed:local
DELETE FROM resource WHERE id LIKE 'demo-%';
DELETE FROM resource_code WHERE id LIKE 'demo-%';

INSERT INTO resource (type, id, version_id, last_updated, patient_id, json) VALUES
('Patient','demo-ana',1,'2026-11-01T00:00:00.000Z','demo-ana',
 '{"resourceType":"Patient","id":"demo-ana","meta":{"versionId":"1","lastUpdated":"2026-11-01T00:00:00.000Z","tag":[{"system":"http://terminology.hl7.org/CodeSystem/v3-ActReason","code":"HTEST","display":"test health data"}]},"identifier":[{"system":"urn:gooluco:demo","value":"DEMO-0001"}],"active":true,"name":[{"use":"official","family":"Prueba (FICTICIA)","given":["Ana"]}],"gender":"female","birthDate":"1980-05-12"}'),
('Patient','demo-beto',1,'2026-11-01T00:00:00.000Z','demo-beto',
 '{"resourceType":"Patient","id":"demo-beto","meta":{"versionId":"1","lastUpdated":"2026-11-01T00:00:00.000Z","tag":[{"system":"http://terminology.hl7.org/CodeSystem/v3-ActReason","code":"HTEST","display":"test health data"}]},"identifier":[{"system":"urn:gooluco:demo","value":"DEMO-0002"}],"active":true,"name":[{"use":"official","family":"Ejemplo (FICTICIO)","given":["Beto"]}],"gender":"male","birthDate":"1972-09-30"}');

INSERT INTO resource (type, id, version_id, last_updated, patient_id, code_system, code, date, json) VALUES
('Observation','demo-ana-talla',1,'2026-11-01T00:00:00.000Z','demo-ana','http://loinc.org','8302-2','2026-11-01T13:00:00.000Z',
 '{"resourceType":"Observation","id":"demo-ana-talla","meta":{"versionId":"1","lastUpdated":"2026-11-01T00:00:00.000Z","profile":["http://hl7.org/fhir/StructureDefinition/bodyheight"]},"status":"final","category":[{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/observation-category","code":"vital-signs","display":"Vital Signs"}]}],"code":{"coding":[{"system":"http://loinc.org","code":"8302-2","display":"Body height"}],"text":"Talla"},"subject":{"reference":"Patient/demo-ana"},"effectiveDateTime":"2026-11-01T13:00:00.000Z","valueQuantity":{"value":160,"unit":"cm","system":"http://unitsofmeasure.org","code":"cm"}}'),
('Observation','demo-ana-peso-1',1,'2026-11-20T12:05:00.000Z','demo-ana','http://loinc.org','29463-7','2026-11-20T12:05:00.000Z',
 '{"resourceType":"Observation","id":"demo-ana-peso-1","meta":{"versionId":"1","lastUpdated":"2026-11-20T12:05:00.000Z","profile":["http://hl7.org/fhir/StructureDefinition/bodyweight"]},"status":"final","category":[{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/observation-category","code":"vital-signs","display":"Vital Signs"}]}],"code":{"coding":[{"system":"http://loinc.org","code":"29463-7","display":"Body weight"}],"text":"Peso (balanza OKOK simulada)"},"subject":{"reference":"Patient/demo-ana"},"effectiveDateTime":"2026-11-20T12:05:00.000Z","valueQuantity":{"value":70.0,"unit":"kg","system":"http://unitsofmeasure.org","code":"kg"}}'),
('Observation','demo-ana-imc-1',1,'2026-11-20T12:05:00.000Z','demo-ana','http://loinc.org','39156-5','2026-11-20T12:05:00.000Z',
 '{"resourceType":"Observation","id":"demo-ana-imc-1","meta":{"versionId":"1","lastUpdated":"2026-11-20T12:05:00.000Z","profile":["http://hl7.org/fhir/StructureDefinition/bmi"]},"status":"final","category":[{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/observation-category","code":"vital-signs","display":"Vital Signs"}]}],"code":{"coding":[{"system":"http://loinc.org","code":"39156-5","display":"Body mass index (BMI) [Ratio]"}],"text":"IMC calculado (peso / talla²)"},"subject":{"reference":"Patient/demo-ana"},"effectiveDateTime":"2026-11-20T12:05:00.000Z","valueQuantity":{"value":27.3,"unit":"kg/m2","system":"http://unitsofmeasure.org","code":"kg/m2"},"derivedFrom":[{"reference":"Observation/demo-ana-peso-1"},{"reference":"Observation/demo-ana-talla"}]}'),
('Observation','demo-beto-peso-1',1,'2026-11-21T01:30:00.000Z','demo-beto','http://loinc.org','29463-7','2026-11-21T01:30:00.000Z',
 '{"resourceType":"Observation","id":"demo-beto-peso-1","meta":{"versionId":"1","lastUpdated":"2026-11-21T01:30:00.000Z","profile":["http://hl7.org/fhir/StructureDefinition/bodyweight"]},"status":"final","category":[{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/observation-category","code":"vital-signs","display":"Vital Signs"}]}],"code":{"coding":[{"system":"http://loinc.org","code":"29463-7","display":"Body weight"}]},"subject":{"reference":"Patient/demo-beto"},"effectiveDateTime":"2026-11-21T01:30:00.000Z","valueQuantity":{"value":85.0,"unit":"kg","system":"http://unitsofmeasure.org","code":"kg"}}');

INSERT INTO resource_code (type, id, system, code) VALUES
('Observation','demo-ana-talla','http://loinc.org','8302-2'),
('Observation','demo-ana-peso-1','http://loinc.org','29463-7'),
('Observation','demo-ana-imc-1','http://loinc.org','39156-5'),
('Observation','demo-beto-peso-1','http://loinc.org','29463-7');
