#!/usr/bin/env python3
"""Genera la colección Postman v2.1 y el environment para validar los usuarios ficticios ana y beto.
Uso: python3 postman/build_collection.py  (escribe gooluco-api.postman_collection.json y gooluco-api-live.postman_environment.json)
"""
import json, os, uuid

HERE = os.path.dirname(os.path.abspath(__file__))
LIVE = "https://gooluco-api.mercadeoperuviano.workers.dev"
SCOPE_APP = "launch/patient openid fhirUser offline_access patient/Patient.rs patient/Observation.crs patient/QuestionnaireResponse.crs"
PSS4 = "https://github.com/CravApp/Gooluco/fhir/Questionnaire/pss4-ema"
FJ = "application/fhir+json"

USERS = {
    "ana": {"name": "Ana", "patient": "demo-ana", "kg": 70.4, "cm": 160, "pss": [1, 3, 2, 1]},
    "beto": {"name": "Beto", "patient": "demo-beto", "kg": 85.2, "cm": 175, "pss": [2, 2, 3, 2]},
}

def js(*lines):
    return [l for block in lines for l in block.strip("\n").split("\n")]

def ev(test=None, pre=None):
    out = []
    if pre:
        out.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": js(pre)}})
    if test:
        out.append({"listen": "test", "script": {"type": "text/javascript", "exec": js(test)}})
    return out

def url(raw):
    return raw  # v2.1 admite la URL como string

def bearer(var):
    return {"type": "bearer", "bearer": [{"key": "token", "value": "{{%s}}" % var, "type": "string"}]}

def form(fields):
    return {"mode": "urlencoded", "urlencoded": [{"key": k, "value": v, "type": "text"} for k, v in fields]}

def raw_json(obj):
    return {"mode": "raw", "raw": json.dumps(obj, indent=2, ensure_ascii=False), "options": {"raw": {"language": "json"}}}

def req(name, method, raw, test, pre=None, body=None, auth=None, headers=None, no_redirect=False, desc=None):
    r = {"method": method, "header": headers or [], "url": url(raw)}
    if body: r["body"] = body
    r["auth"] = auth or {"type": "noauth"}
    if desc: r["description"] = desc
    item = {"name": name, "event": ev(test, pre), "request": r}
    if no_redirect:
        item["protocolProfileBehavior"] = {"followRedirects": False}
    return item

H_FHIR = [{"key": "Accept", "value": FJ}]
H_FHIR_POST = H_FHIR + [{"key": "Content-Type", "value": FJ}]

PKCE_PRE = r"""
// PKCE S256 (RFC 7636): code_verifier aleatorio y code_challenge = BASE64URL(SHA256(verifier))
const CJ = require('crypto-js');
const b64url = (w) => CJ.enc.Base64.stringify(w).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const verifier = b64url(CJ.lib.WordArray.random(48));
const challenge = b64url(CJ.SHA256(verifier));
const state = b64url(CJ.lib.WordArray.random(12));
pm.collectionVariables.set('{P}_code_verifier', verifier);
pm.collectionVariables.set('{P}_code_challenge', challenge);
pm.collectionVariables.set('{P}_state', state);
"""

AUTHZ_TEST = r"""
pm.test('302 hacia redirect_uri (consentimiento aceptado)', () => {
  pm.response.to.have.status(302);
  pm.expect(pm.response.headers.get('Location')).to.match(new RegExp('^' + pm.environment.get('redirect_uri').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
});
const loc = pm.response.headers.get('Location') || '';
const qs = {};
(loc.split('?')[1] || '').split('&').forEach((kv) => { const [k, v] = kv.split('='); if (k) qs[k] = decodeURIComponent(v || ''); });
pm.test('la respuesta trae code', () => pm.expect(qs.code, JSON.stringify(qs)).to.be.a('string').and.not.empty);
pm.test('state coincide (anti-CSRF)', () => pm.expect(qs.state).to.eql(pm.collectionVariables.get('{P}_state')));
pm.test('sin error OAuth', () => pm.expect(qs.error).to.be.undefined);
pm.collectionVariables.set('{P}_code', qs.code || '');
"""

DECODE = r"""
const CJ = require('crypto-js');
const jwtPayload = (t) => { let p = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'); while (p.length % 4) p += '='; return JSON.parse(CJ.enc.Base64.parse(p).toString(CJ.enc.Utf8)); };
"""

def token_test(p, patient, expect_scopes, store_prefix, oidc=True):
    t = DECODE + r"""
pm.test('200 token emitido', () => pm.response.to.have.status(200));
const t = pm.response.json();
pm.test('token_type Bearer y expires_in', () => { pm.expect(t.token_type.toLowerCase()).to.eql('bearer'); pm.expect(t.expires_in).to.be.above(0); });
pm.test('Cache-Control: no-store', () => pm.expect(pm.response.headers.get('Cache-Control')).to.include('no-store'));
pm.test('contexto patient = %(patient)s', () => pm.expect(t.patient).to.eql('%(patient)s'));
pm.test('scopes concedidos', () => { const s = t.scope.split(' '); %(scopes)s.forEach((x) => pm.expect(s).to.include(x)); });
const at = jwtPayload(t.access_token);
pm.test('access_token ES256 con aud = base FHIR', () => { pm.expect(at.aud).to.eql(pm.environment.get('fhir_base')); pm.expect(at.patient).to.eql('%(patient)s'); });
pm.collectionVariables.set('%(sp)s_access_token', t.access_token);
pm.collectionVariables.set('%(sp)s_patient', t.patient || '');
""" % {"patient": patient, "scopes": json.dumps(expect_scopes), "sp": store_prefix}
    if oidc:
        t += r"""
pm.test('id_token con fhirUser del paciente', () => pm.expect(jwtPayload(t.id_token).fhirUser).to.match(/\/Patient\/%(patient)s$/));
pm.test('refresh_token (offline_access)', () => pm.expect(t.refresh_token).to.be.a('string').and.not.empty);
pm.collectionVariables.set('%(sp)s_refresh_token', t.refresh_token);
""" % {"patient": patient, "sp": store_prefix}
    return t

def login_items(p, user, patient, scope, store_prefix, label):
    authz = req(
        f"{label} 1 · Autorizar como «{user}» (formulario de consentimiento de prueba)", "POST", "{{base_url}}/auth/authorize",
        AUTHZ_TEST.replace("{P}", p), pre=PKCE_PRE.replace("{P}", p), no_redirect=True,
        body=form([
            ("response_type", "code"), ("client_id", "gooluco-android"), ("redirect_uri", "{{redirect_uri}}"),
            ("scope", scope), ("state", "{{%s_state}}" % p), ("aud", "{{fhir_base}}"),
            ("code_challenge", "{{%s_code_challenge}}" % p), ("code_challenge_method", "S256"),
            ("decision", "allow"), ("user", user),
        ]),
        desc="Simula el clic «Autorizar» en la página de prueba. followRedirects está desactivado para leer el `code` del header Location.",
    )
    oidc = "openid" in scope
    scopes = [s for s in scope.split(" ")]
    tok = req(
        f"{label} 2 · Canjear code + code_verifier en /auth/token", "POST", "{{base_url}}/auth/token",
        token_test(p, patient, scopes, store_prefix, oidc),
        body=form([
            ("grant_type", "authorization_code"), ("code", "{{%s_code}}" % p), ("redirect_uri", "{{redirect_uri}}"),
            ("code_verifier", "{{%s_code_verifier}}" % p), ("client_id", "gooluco-android"),
        ]),
    )
    return [authz, tok]

def obs_body(p, loinc, display, value, unit, now_var, derived=None, vital=True):
    o = {
        "resourceType": "Observation", "status": "final",
        "code": {"coding": [{"system": "http://loinc.org", "code": loinc, "display": display}]},
        "subject": {"reference": "Patient/{{%s_patient}}" % p},
        "effectiveDateTime": "{{%s}}" % now_var,
        "device": {"display": "Balanza OKOK (simulada, Postman)"},
        "valueQuantity": {"value": "__VALUE__", "unit": unit, "system": "http://unitsofmeasure.org", "code": unit},
        "note": [{"text": "Dato FICTICIO de prueba (Postman)"}],
    }
    if vital:
        o["category"] = [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "vital-signs"}]}]
    if derived:
        o["derivedFrom"] = [{"reference": "Observation/{{%s}}" % d} for d in derived]
    b = raw_json(o)
    b["raw"] = b["raw"].replace('"__VALUE__"', str(value))
    return b

def created_test(p, var, loinc, value, profile):
    return r"""
pm.test('201 Created', () => pm.response.to.have.status(201));
pm.test('Content-Type application/fhir+json', () => pm.expect(pm.response.headers.get('Content-Type')).to.include('application/fhir+json'));
const o = pm.response.json();
pm.test('Location y ETag W/"1"', () => { pm.expect(pm.response.headers.get('Location')).to.include('/fhir/Observation/' + o.id + '/_history/1'); pm.expect(pm.response.headers.get('ETag')).to.eql('W/"1"'); });
pm.test('LOINC %(loinc)s, valor %(value)s y subject propio', () => {
  pm.expect(o.code.coding[0].code).to.eql('%(loinc)s');
  pm.expect(o.valueQuantity.value).to.eql(%(value)s);
  pm.expect(o.subject.reference).to.eql('Patient/' + pm.collectionVariables.get('%(p)s_patient'));
});
%(profile)s
pm.collectionVariables.set('%(var)s', o.id);
""" % {"loinc": loinc, "value": value, "p": p, "var": var,
       "profile": ("pm.test('perfil R4 %s', () => pm.expect(o.meta.profile).to.include('http://hl7.org/fhir/StructureDefinition/%s'));" % (profile, profile)) if profile else ""}

def user_folder(p, u):
    pid = u["patient"]
    kg, cm = u["kg"], u["cm"]
    bmi = round(kg / (cm / 100) ** 2, 1)
    items = login_items(p, p, pid, SCOPE_APP, p, u["name"])
    items.append(req(
        f"{u['name']} 3 · Leer Patient propio", "GET", "{{fhir_base}}/Patient/{{%s_patient}}" % p,
        r"""
pm.test('200 OK', () => pm.response.to.have.status(200));
pm.test('application/fhir+json', () => pm.expect(pm.response.headers.get('Content-Type')).to.include('application/fhir+json'));
const r = pm.response.json();
pm.test('Patient/%(pid)s', () => { pm.expect(r.resourceType).to.eql('Patient'); pm.expect(r.id).to.eql('%(pid)s'); });
pm.test('es un paciente FICTICIO', () => pm.expect(r.name[0].family).to.match(/FICTICI/));
""" % {"pid": pid}, auth=bearer(f"{p}_access_token"), headers=H_FHIR))
    now_pre = "pm.collectionVariables.set('%s_now', new Date().toISOString());\npm.collectionVariables.set('%s_today', new Date().toISOString().slice(0, 10));" % (p, p)
    items.append(req(f"{u['name']} 4 · POST peso {kg} kg (LOINC 29463-7)", "POST", "{{fhir_base}}/Observation",
                     created_test(p, f"{p}_weight_id", "29463-7", kg, "bodyweight"), pre=now_pre,
                     body=obs_body(p, "29463-7", "Body weight", kg, "kg", f"{p}_now"), auth=bearer(f"{p}_access_token"), headers=H_FHIR_POST))
    items.append(req(f"{u['name']} 5 · POST talla {cm} cm (LOINC 8302-2)", "POST", "{{fhir_base}}/Observation",
                     created_test(p, f"{p}_height_id", "8302-2", cm, "bodyheight"),
                     body=obs_body(p, "8302-2", "Body height", cm, "cm", f"{p}_now"), auth=bearer(f"{p}_access_token"), headers=H_FHIR_POST))
    items.append(req(f"{u['name']} 6 · POST IMC {bmi} kg/m2 (LOINC 39156-5, derivedFrom)", "POST", "{{fhir_base}}/Observation",
                     created_test(p, f"{p}_bmi_id", "39156-5", bmi, "bmi") + "pm.test('derivedFrom peso y talla', () => pm.expect(o.derivedFrom.length).to.eql(2));",
                     body=obs_body(p, "39156-5", "Body mass index (BMI) [Ratio]", bmi, "kg/m2", f"{p}_now", derived=[f"{p}_weight_id", f"{p}_height_id"]),
                     auth=bearer(f"{p}_access_token"), headers=H_FHIR_POST))
    items.append(req(
        f"{u['name']} 7 · Buscar Observations propias (patient + code + date)", "GET",
        "{{fhir_base}}/Observation?patient=Patient/{{%s_patient}}&code=http://loinc.org|29463-7,http://loinc.org|8302-2,http://loinc.org|39156-5&date=ge{{%s_today}}&_count=100" % (p, p),
        r"""
pm.test('200 Bundle searchset', () => { pm.response.to.have.status(200); const b = pm.response.json(); pm.expect(b.resourceType).to.eql('Bundle'); pm.expect(b.type).to.eql('searchset'); });
const b = pm.response.json();
const ids = (b.entry || []).map((e) => e.resource.id);
pm.test('incluye peso, talla e IMC recién creados', () => ['%(p)s_weight_id', '%(p)s_height_id', '%(p)s_bmi_id'].forEach((v) => pm.expect(ids).to.include(pm.collectionVariables.get(v))));
pm.test('todas son del paciente propio', () => (b.entry || []).forEach((e) => pm.expect(e.resource.subject.reference).to.eql('Patient/%(pid)s')));
pm.test('solo códigos pedidos', () => (b.entry || []).forEach((e) => pm.expect(['29463-7', '8302-2', '39156-5']).to.include(e.resource.code.coding[0].code)));
pm.test('total >= 3', () => pm.expect(b.total).to.be.at.least(3));
""" % {"p": p, "pid": pid}, auth=bearer(f"{p}_access_token"), headers=H_FHIR))
    a = u["pss"]
    total = a[0] + (4 - a[1]) + (4 - a[2]) + a[3]
    qr = {
        "resourceType": "QuestionnaireResponse", "questionnaire": PSS4, "status": "completed",
        "subject": {"reference": "Patient/{{%s_patient}}" % p}, "authored": "{{%s_now}}" % p,
        "item": [{"linkId": f"pss4-{i+1}", "answer": [{"valueCoding": {"system": PSS4 + "/likert", "code": str(v)}}]} for i, v in enumerate(a)]
        + [{"linkId": "pss4-total", "answer": [{"valueInteger": total}]},
           {"linkId": "actividad", "answer": [{"valueCoding": {"system": PSS4 + "/actividad", "code": "trabajando_estudiando"}}]},
           {"linkId": "lugar", "answer": [{"valueCoding": {"system": PSS4 + "/lugar", "code": "casa"}}]},
           {"linkId": "social", "answer": [{"valueBoolean": False}]}],
    }
    items.append(req(f"{u['name']} 8 · POST QuestionnaireResponse PSS-4 (total {total})", "POST", "{{fhir_base}}/QuestionnaireResponse",
                     r"""
pm.test('201 Created', () => pm.response.to.have.status(201));
const q = pm.response.json();
pm.test('PSS-4 del paciente propio', () => { pm.expect(q.questionnaire).to.eql('%(q)s'); pm.expect(q.subject.reference).to.eql('Patient/%(pid)s'); });
pm.test('total PSS-4 = %(total)s (ítems 2 y 3 invertidos)', () => pm.expect(q.item.find((i) => i.linkId === 'pss4-total').answer[0].valueInteger).to.eql(%(total)s));
pm.collectionVariables.set('%(p)s_qr_id', q.id);
""" % {"q": PSS4, "pid": pid, "total": total, "p": p},
                     body=raw_json(qr), auth=bearer(f"{p}_access_token"), headers=H_FHIR_POST))
    items.append(req(f"{u['name']} 9 · Buscar QuestionnaireResponse PSS-4 propias", "GET",
                     "{{fhir_base}}/QuestionnaireResponse?questionnaire=%s&authored=ge{{%s_today}}" % (PSS4, p),
                     r"""
pm.test('200 Bundle', () => pm.response.to.have.status(200));
const b = pm.response.json();
pm.test('incluye la respuesta creada', () => pm.expect((b.entry || []).map((e) => e.resource.id)).to.include(pm.collectionVariables.get('%(p)s_qr_id')));
pm.test('solo del paciente propio', () => (b.entry || []).forEach((e) => pm.expect(e.resource.subject.reference).to.eql('Patient/%(pid)s')));
""" % {"p": p, "pid": pid}, auth=bearer(f"{p}_access_token"), headers=H_FHIR))
    items.append(req(f"{u['name']} 10 · Refresh token (rotación)", "POST", "{{base_url}}/auth/token",
                     r"""
pm.test('200 nuevo token', () => pm.response.to.have.status(200));
const t = pm.response.json();
pm.test('mismo paciente y scope', () => { pm.expect(t.patient).to.eql('%(pid)s'); pm.expect(t.scope.split(' ')).to.include('patient/Observation.crs'); });
pm.test('refresh_token rotado (distinto del anterior)', () => { pm.expect(t.refresh_token).to.be.a('string'); pm.expect(t.refresh_token).to.not.eql(pm.collectionVariables.get('%(p)s_old_refresh_token')); });
pm.test('access_token nuevo', () => pm.expect(t.access_token).to.not.eql(pm.collectionVariables.get('%(p)s_old_access_token')));
pm.collectionVariables.set('%(p)s_access_token', t.access_token);
pm.collectionVariables.set('%(p)s_refresh_token', t.refresh_token);
""" % {"pid": pid, "p": p},
                     pre="pm.collectionVariables.set('%s_old_refresh_token', pm.collectionVariables.get('%s_refresh_token'));\npm.collectionVariables.set('%s_old_access_token', pm.collectionVariables.get('%s_access_token'));" % (p, p, p, p),
                     body=form([("grant_type", "refresh_token"), ("refresh_token", "{{%s_refresh_token}}" % p), ("client_id", "gooluco-android")])))
    items.append(req(f"{u['name']} 11 · Reusar el refresh token anterior → invalid_grant", "POST", "{{base_url}}/auth/token",
                     r"""
pm.test('400', () => pm.response.to.have.status(400));
pm.test('error invalid_grant', () => pm.expect(pm.response.json().error).to.eql('invalid_grant'));
""", body=form([("grant_type", "refresh_token"), ("refresh_token", "{{%s_old_refresh_token}}" % p), ("client_id", "gooluco-android")])))
    items.append(req(f"{u['name']} 12 · El access token renovado funciona", "GET", "{{fhir_base}}/Patient/{{%s_patient}}" % p,
                     "pm.test('200 OK con el token renovado', () => pm.response.to.have.status(200));\npm.test('Patient/%s', () => pm.expect(pm.response.json().id).to.eql('%s'));" % (pid, pid),
                     auth=bearer(f"{p}_access_token"), headers=H_FHIR))
    return {"name": u["name"], "description": f"Flujo SMART completo como el usuario ficticio «{p}» (Patient/{pid}).", "item": items}

def forbidden(name, method, raw, tokvar, code=403, extra="", body=None, headers=H_FHIR):
    t = r"""
pm.test('%(code)s', () => pm.response.to.have.status(%(code)s));
const r = pm.response.json();
pm.test('OperationOutcome', () => pm.expect(r.resourceType).to.eql('OperationOutcome'));
%(extra)s
""" % {"code": code, "extra": extra}
    return req(name, method, raw, t, auth=bearer(tokvar) if tokvar else None, body=body, headers=headers)

discovery = {"name": "Descubrimiento", "item": [
    req("metadata (CapabilityStatement)", "GET", "{{fhir_base}}/metadata", r"""
pm.test('200 application/fhir+json', () => { pm.response.to.have.status(200); pm.expect(pm.response.headers.get('Content-Type')).to.include('application/fhir+json'); });
const cs = pm.response.json();
pm.test('CapabilityStatement FHIR 4.0.1', () => { pm.expect(cs.resourceType).to.eql('CapabilityStatement'); pm.expect(cs.fhirVersion).to.eql('4.0.1'); });
pm.test('seguridad SMART-on-FHIR', () => pm.expect(cs.rest[0].security.service[0].coding[0].code).to.eql('SMART-on-FHIR'));
pm.test('recursos soportados', () => { const t = cs.rest[0].resource.map((x) => x.type); ['Patient', 'Observation', 'QuestionnaireResponse', 'Questionnaire'].forEach((x) => pm.expect(t).to.include(x)); });
""", headers=H_FHIR),
    req("smart-configuration", "GET", "{{fhir_base}}/.well-known/smart-configuration", r"""
pm.test('200', () => pm.response.to.have.status(200));
const c = pm.response.json();
pm.test('endpoints del servidor', () => { pm.expect(c.authorization_endpoint).to.eql(pm.environment.get('base_url') + '/auth/authorize'); pm.expect(c.token_endpoint).to.eql(pm.environment.get('base_url') + '/auth/token'); });
pm.test('PKCE S256 y sin plain', () => { pm.expect(c.code_challenge_methods_supported).to.include('S256'); pm.expect(c.code_challenge_methods_supported).to.not.include('plain'); });
pm.test('capabilities standalone de paciente', () => ['launch-standalone', 'client-public', 'context-standalone-patient', 'permission-patient', 'permission-v2'].forEach((x) => pm.expect(c.capabilities).to.include(x)));
"""),
    req("Questionnaire PSS-4 (público)", "GET", "{{fhir_base}}/Questionnaire/pss4-ema", r"""
pm.test('200', () => pm.response.to.have.status(200));
const q = pm.response.json();
pm.test('4 ítems PSS-4', () => pm.expect(q.item.filter((i) => /^pss4-\d$/.test(i.linkId)).length).to.eql(4));
""", headers=H_FHIR),
]}

cross = {"name": "Seguridad cruzada", "description": "Requiere haber corrido antes las carpetas Ana y Beto (usa sus tokens e ids).", "item": [
    forbidden("Sin token → 401", "GET", "{{fhir_base}}/Observation", None, 401,
              "pm.test('WWW-Authenticate: Bearer', () => pm.expect(pm.response.headers.get('WWW-Authenticate')).to.match(/^Bearer/));"),
    forbidden("Token manipulado → 401 invalid_token", "GET", "{{fhir_base}}/Observation", "tampered_token", 401,
              "pm.test('error=invalid_token', () => pm.expect(pm.response.headers.get('WWW-Authenticate')).to.include('invalid_token'));"),
    forbidden("Ana lee Patient/demo-beto → 403", "GET", "{{fhir_base}}/Patient/demo-beto", "ana_access_token", 403,
              "pm.test('issue forbidden', () => pm.expect(r.issue[0].code).to.eql('forbidden'));"),
    forbidden("Beto lee el peso de Ana → 403", "GET", "{{fhir_base}}/Observation/{{ana_weight_id}}", "beto_access_token", 403,
              "pm.test('issue forbidden', () => pm.expect(r.issue[0].code).to.eql('forbidden'));"),
    forbidden("Beto lee la respuesta PSS-4 de Ana → 403", "GET", "{{fhir_base}}/QuestionnaireResponse/{{ana_qr_id}}", "beto_access_token", 403),
    req("Beto busca Observations de Ana → Bundle vacío", "GET", "{{fhir_base}}/Observation?patient=Patient/demo-ana", r"""
pm.test('200', () => pm.response.to.have.status(200));
pm.test('total 0 (filtrado por compartimento)', () => pm.expect(pm.response.json().total).to.eql(0));
""", auth=bearer("beto_access_token"), headers=H_FHIR),
    forbidden("Ana crea un peso para Beto → 403", "POST", "{{fhir_base}}/Observation", "ana_access_token", 403,
              body=obs_body("beto", "29463-7", "Body weight", 99.9, "kg", "beto_now"), headers=H_FHIR_POST),
    *login_items("lim", "ana", "demo-ana", "launch/patient patient/Observation.rs", "lim", "Scope limitado"),
    forbidden("Scope limitado: POST Observation sin .c → 403", "POST", "{{fhir_base}}/Observation", "lim_access_token", 403,
              "pm.test('insufficient_scope', () => pm.expect(pm.response.headers.get('WWW-Authenticate')).to.include('insufficient_scope'));",
              body=obs_body("ana", "29463-7", "Body weight", 70.0, "kg", "ana_now"), headers=H_FHIR_POST),
    forbidden("Scope limitado: QuestionnaireResponse sin scope → 403", "GET", "{{fhir_base}}/QuestionnaireResponse", "lim_access_token", 403),
    forbidden("Scope limitado: Patient sin scope → 403", "GET", "{{fhir_base}}/Patient/demo-ana", "lim_access_token", 403),
    req("Scope limitado: sí puede buscar sus Observations", "GET", "{{fhir_base}}/Observation?code=29463-7", r"""
pm.test('200', () => pm.response.to.have.status(200));
pm.test('solo de Ana', () => (pm.response.json().entry || []).forEach((e) => pm.expect(e.resource.subject.reference).to.eql('Patient/demo-ana')));
""", auth=bearer("lim_access_token"), headers=H_FHIR),
]}
# token manipulado: se genera en un pre-request de la colección cruzada
cross["event"] = ev(pre="const t = pm.collectionVariables.get('ana_access_token') || 'x.y.z';\npm.collectionVariables.set('tampered_token', t.slice(0, -4) + (t.slice(-4) === 'AAAA' ? 'BBBB' : 'AAAA'));")

oauth_ui = {"name": "OAuth 2.0 interactivo (Postman UI)", "description":
    "Para probar a mano en Postman: pestaña Authorization → Get New Access Token. Abre la página de consentimiento de PRUEBA en el navegador; elige ana o beto. No se ejecuta con newman.",
    "item": [req("Patient en contexto (token de Postman OAuth 2.0)", "GET", "{{fhir_base}}/Patient/{{oauth_patient}}",
                 "pm.test('200 con el token OAuth de Postman', () => pm.response.to.have.status(200));",
                 headers=H_FHIR,
                 auth={"type": "oauth2", "oauth2": [
                     {"key": "grant_type", "value": "authorization_code_with_pkce", "type": "string"},
                     {"key": "authUrl", "value": "{{base_url}}/auth/authorize", "type": "string"},
                     {"key": "accessTokenUrl", "value": "{{base_url}}/auth/token", "type": "string"},
                     {"key": "refreshTokenUrl", "value": "{{base_url}}/auth/token", "type": "string"},
                     {"key": "clientId", "value": "gooluco-android", "type": "string"},
                     {"key": "clientSecret", "value": "", "type": "string"},
                     {"key": "redirect_uri", "value": "{{postman_callback}}", "type": "string"},
                     {"key": "useBrowser", "value": False, "type": "boolean"},
                     {"key": "scope", "value": SCOPE_APP, "type": "string"},
                     {"key": "state", "value": "postman-{{$randomUUID}}", "type": "string"},
                     {"key": "challengeAlgorithm", "value": "S256", "type": "string"},
                     {"key": "client_authentication", "value": "body", "type": "string"},
                     {"key": "authRequestParams", "value": [{"key": "aud", "value": "{{fhir_base}}", "enabled": True, "send_as": "request_url"}], "type": "any"},
                     {"key": "tokenName", "value": "gooluco-smart", "type": "string"},
                     {"key": "addTokenTo", "value": "header", "type": "string"},
                     {"key": "headerPrefix", "value": "Bearer", "type": "string"},
                 ]})]}

collection_vars = []
for p in ["ana", "beto", "lim"]:
    for k in ["code_verifier", "code_challenge", "state", "code", "access_token", "refresh_token", "patient"]:
        collection_vars.append({"key": f"{p}_{k}", "value": ""})
collection_vars += [{"key": "tampered_token", "value": ""}, {"key": "oauth_patient", "value": "demo-ana"}]

collection = {
    "info": {
        "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "gooluco-api-postman")),
        "name": "Gooluco API — SMART on FHIR (ana / beto, PRUEBA)",
        "description": "Valida la API FHIR R4 + SMART App Launch 2.x de Gooluco con los usuarios FICTICIOS ana y beto. Orden: Descubrimiento → Ana → Beto → Seguridad cruzada. Ver postman/README.md.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": [discovery, user_folder("ana", USERS["ana"]), user_folder("beto", USERS["beto"]), cross, oauth_ui],
    "variable": collection_vars,
    "auth": {"type": "noauth"},
}

env = {
    "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "gooluco-api-live-env")),
    "name": "Gooluco API — live (workers.dev)",
    "values": [
        {"key": "base_url", "value": LIVE, "type": "default", "enabled": True},
        {"key": "fhir_base", "value": LIVE + "/fhir", "type": "default", "enabled": True},
        {"key": "redirect_uri", "value": "http://127.0.0.1:53682/callback", "type": "default", "enabled": True},
        {"key": "postman_callback", "value": "https://oauth.pstmn.io/v1/callback", "type": "default", "enabled": True},
    ],
    "_postman_variable_scope": "environment",
}

with open(os.path.join(HERE, "gooluco-api.postman_collection.json"), "w") as f:
    json.dump(collection, f, indent=2, ensure_ascii=False)
with open(os.path.join(HERE, "gooluco-api-live.postman_environment.json"), "w") as f:
    json.dump(env, f, indent=2, ensure_ascii=False)
print("ok")
