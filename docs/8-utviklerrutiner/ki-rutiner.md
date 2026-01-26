# Utviklerrutiner for KI-sjekken (ROB)

Dette dokumentet beskriver rutiner utviklere må følge ved vedlikehold og utvikling av KI-sjekken.

## Innhold

- [Oppgradering av modellversjon](#oppgradering-av-modellversjon)
- [Endring av systemprompt](#endring-av-systemprompt)
- [Overvåking av Azure OpenAI retningslinjer](#overvåking-av-azure-openai-retningslinjer)
- [Oppdatering av kunnskapsgrunnlag](#oppdatering-av-kunnskapsgrunnlag)
- [Evaluering av språkmodell](#evaluering-av-språkmodell)
- [Verifisering av hallusinering](#verifisering-av-hallusinering)
- [Feilhåndtering ved loggsletting](#feilhåndtering-ved-loggsletting)

---

## Oppgradering av modellversjon

**ROS-referanse:** 29023

**Når:** Når Microsoft varsler om at modellversjon utgår, eller ved ønske om oppgradering.

**Rutine:**

1. Les Microsofts release notes for ny versjon
2. Oppdater modellversjon i dev/test først
3. Kjør full testsuite inkl. benchmarks (se [ki-tekstvalideringstjeneste.md](../5-ki/ki-tekstvalideringstjeneste.md))
4. Verifiser at 90%-målet for testcases opprettholdes
5. Dokumenter eventuelle endringer i oppførsel
6. Deploy til prod med toggle klar for rask rollback

---

## Endring av systemprompt

**ROS-referanse:** 27852, 27853

**Når:** Ved behov for å justere KI-sjekken oppførsel.

**Rutine:**

1. Dokumenter formål med endring i PR-beskrivelse
2. Følg klarspråk-prinsipper (se [ki-tekstvalideringstjeneste.md](../5-ki/ki-tekstvalideringstjeneste.md))
3. Kjør automatiske tester
4. Verifiser mot benchmarks
5. Test manuelt i dev med representative eksempler
6. Merge via normal code review

**Rollback:** Prompten er versjonskontrollert og kan enkelt reverteres via git.

---

## Overvåking av Azure OpenAI retningslinjer

**ROS-referanse:** 29262

**Når:** Løpende, minimum kvartalsvis.

**Rutine:**

1. Sjekk [Azure OpenAI Service documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/) for oppdateringer
2. Les gjennom eventuelle nye retningslinjer
3. Vurder om vår bruk er i tråd med retningslinjene
4. Dokumenter avvik og tiltak i teamets backlog

---

## Periodisk sjekk av oppsett

**ROS-referanse:** 29263, 29025

**Når:** Kvartalsvis eller ved endring av infrastruktur.

**Rutine:**

1. Verifiser i Azure Portal at Content Filter er aktivert ("Abuse monitoring")
2. Verifiser at deployment fortsatt er "Standard" (ikke Global)
3. Verifiser at modellen kjører i EU/EØS-region (Norway East / Sweden Central)

---

## Oppdatering av kunnskapsgrunnlag

**ROS-referanse:** 27544

**Når:** Ved endringer i lovverk, diskrimineringsgrunnlag, eller NAV-retningslinjer.

**Rutine:**

1. Identifiser hvilke deler av prompten som påvirkes
2. Oppdater prompt med ny informasjon
3. Oppdater benchmarks/testcases om nødvendig
4. Følg rutine for [Endring av systemprompt](#endring-av-systemprompt)

---

## Evaluering av språkmodell

**ROS-referanse:** 27868

**Når:** Ved valg av ny modell eller oppgradering av eksisterende.

**Rutine:**

1. Dokumenter hvilken modell som vurderes og hvorfor
2. Verifiser at modellen støtter standard deployment i EU/EØS
3. Kjør full benchmark-suite mot eksisterende testcases
4. Sammenlign treffprosent med nåværende modell
5. Dokumenter eventuelle risikoer eller endringer i oppførsel
6. Få godkjenning fra teamet før produksjonssetting
7. Oppdater dokumentasjon med ny modellversjon

---

## Verifisering av hallusinering

**ROS-referanse:** 27854

**Når:** Ved oppgradering av modell, endring av prompt, eller ved mistanke om feil.

**Rutine:**

1. Kjør testcases som verifiserer faktisk innhold i responser
2. Sjekk at KI-sjekken ikke genererer informasjon utover det som etterspørres
3. Verifiser at vurderingene er basert på faktisk innhold i teksten
4. Dokumenter eventuelle avvik og korriger prompt om nødvendig

---

## Feilhåndtering ved loggsletting

**ROS-referanse:** 29330

**Når:** Dersom automatisk sletting av logger feiler.

**Rutine:**

1. Motta varsel om feilet slettejobb (via monitorering)
2. Undersøk årsak til feil
3. Kjør manuell sletting om nødvendig
4. Verifiser at logger eldre enn retensjon er slettet
5. Dokumenter hendelsen og ev. tiltak

---

## Se også

- [ki-tekstvalideringstjeneste.md](../5-ki/ki-tekstvalideringstjeneste.md) - Teknisk dokumentasjon av KI-tekstvalideringstjenesten
- [tilgangsstyring.md](../3-sikkerhet/tilgangsstyring.md) - Tilgangskontroll og roller
- [ros-ki-pilot.md](../7-akseptansetest-og-ros/ros-ki-pilot.md) - ROS-analyse for KI-sjekken
