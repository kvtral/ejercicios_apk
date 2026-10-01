#!/usr/bin/env python3
"""Genera app/src/main/assets/exercises.json: ejercicios, poses animadas y rutinas predefinidas.

Edita este archivo y ejecuta: python3 tools/generate_catalog.py
Después revisa las poses con: python3 tools/preview_poses.py
"""
import json, os, sys

def P(**kw):
    return kw

STAND = P()
ARMS_FWD = dict(armF=80, foreF=85, armB=80, foreB=85)

# ---- poses reutilizables ----
squat_top = P(**ARMS_FWD)
squat_bot = P(torso=140, head=150, thighF=85, shinF=-35, thighB=85, shinB=-35, armF=95, foreF=95, armB=95, foreB=95)

plank_hi = dict(torso=112.7, head=115, thighF=-67.3, shinF=-67.3, thighB=-67.3, shinB=-67.3)
push_bot = dict(torso=98, head=100, thighF=-82, shinF=-82, thighB=-82, shinB=-82,
                armF=-88, foreF=44, armB=-88, foreB=44)

lunge_top = P(torso=180, thighF=28, shinF=-12, thighB=-28, shinB=-40)
lunge_bot = P(torso=178, thighF=85, shinF=-5, thighB=-12, shinB=-82)

row_base = dict(torso=118, head=125, thighF=22, shinF=-12, thighB=22, shinB=-12)
row_top = dict(row_base, armF=0, foreF=0, armB=0, foreB=0)
row_pull = dict(row_base, armF=-80, foreF=0, armB=-80, foreB=0)

bridge_down = dict(torso=-90, head=-90, thighF=135, shinF=-12, thighB=135, shinB=-12,
                   armF=90, foreF=90, armB=90, foreB=90)
bridge_up = dict(torso=-68, head=-75, thighF=100, shinF=-8, thighB=100, shinB=-8,
                 armF=90, foreF=90, armB=90, foreB=90)

goblet_arms = dict(armF=25, foreF=165, armB=25, foreB=165)
goblet_bot = dict(squat_bot, **goblet_arms)
goblet_top = dict(goblet_arms)

ex = []
def add(id, name, eq, muscles, desc, tips, view, anchor, period, prop, frames, mat=False, mode="reps"):
    ex.append(dict(id=id, name=name, equipment=eq, muscles=muscles, description=desc, tips=tips,
                   animation=dict(view=view, anchor=anchor, periodMs=period, prop=prop, mat=mat, frames=frames)))

# ================= PESO CORPORAL =================
add("squat", "Sentadilla", "BODYWEIGHT", ["Cuádriceps", "Glúteos", "Isquiotibiales"],
    "De pie con los pies a la anchura de los hombros, baja la cadera atrás y abajo como si te sentaras en una silla y vuelve a subir.",
    ["Mantén el pecho erguido y la espalda neutra", "Las rodillas siguen la dirección de las puntas de los pies", "Empuja el suelo con los talones al subir"],
    "side", "footF", 2200, "none", [squat_top, squat_bot])

add("pushup", "Flexiones", "BODYWEIGHT", ["Pectoral", "Tríceps", "Hombros", "Core"],
    "Desde plancha alta con las manos bajo los hombros, flexiona los codos hasta acercar el pecho al suelo y empuja para volver arriba.",
    ["Cuerpo en línea recta de cabeza a talones", "Codos a unos 45° del cuerpo", "Si es duro, apoya las rodillas"],
    "side", "footF", 2000, "none", [plank_hi, push_bot], mat=True)

add("lunge", "Zancada estática", "BODYWEIGHT", ["Cuádriceps", "Glúteos"],
    "Con un pie adelantado y otro atrasado, baja la rodilla trasera hacia el suelo y sube. Cambia de pierna a mitad de las series.",
    ["Torso vertical", "La rodilla delantera no sobrepasa en exceso la punta del pie", "Baja controlado"],
    "side", "footF", 2200, "none", [lunge_top, lunge_bot])

add("plank", "Plancha", "BODYWEIGHT", ["Core", "Hombros"],
    "Apoya antebrazos y puntas de los pies manteniendo el cuerpo recto. Aguanta la posición durante todo el tiempo.",
    ["Aprieta glúteos y abdomen", "No hundas ni eleves la cadera", "Respira de forma continua"],
    "side", "footF", 3000, "none",
    [dict(torso=101.5, head=104, thighF=-78.5, shinF=-78.5, thighB=-78.5, shinB=-78.5, armF=0, foreF=90, armB=0, foreB=90),
     dict(torso=102.5, head=103, thighF=-77.5, shinF=-77.5, thighB=-77.5, shinB=-77.5, armF=2, foreF=90, armB=2, foreB=90)], mat=True)

add("jumping_jacks", "Saltos de tijera", "BODYWEIGHT", ["Cardio", "Piernas", "Hombros"],
    "Salta abriendo piernas y elevando los brazos por encima de la cabeza; vuelve a cerrar con otro salto.",
    ["Aterriza suave sobre la parte delantera del pie", "Mantén un ritmo constante"],
    "front", "hip", 1000, "none",
    [dict(armF=12, foreF=12, armB=12, foreB=12, thighF=4, shinF=4, thighB=4, shinB=4),
     dict(armF=160, foreF=172, armB=160, foreB=172, thighF=22, shinF=22, thighB=22, shinB=22, lift=3)])

add("mountain_climbers", "Escaladores", "BODYWEIGHT", ["Core", "Cardio", "Hombros"],
    "En plancha alta, lleva alternativamente cada rodilla hacia el pecho de forma rápida.",
    ["Hombros sobre las manos", "Cadera baja y estable", "Respira rítmicamente"],
    "side", "handF", 900, "none",
    [dict(plank_hi, thighF=40, shinF=-60),
     dict(plank_hi, thighB=40, shinB=-60)], mat=True)

add("glute_bridge", "Puente de glúteo", "BODYWEIGHT", ["Glúteos", "Isquiotibiales", "Core"],
    "Tumbado boca arriba con las rodillas flexionadas, eleva la cadera apretando glúteos hasta alinear rodillas, cadera y hombros.",
    ["Empuja con los talones", "No arquees la zona lumbar", "Pausa un segundo arriba"],
    "side", "shoulder", 2200, "none", [bridge_down, bridge_up], mat=True)

add("crunch", "Abdominales (crunch)", "BODYWEIGHT", ["Abdomen"],
    "Tumbado boca arriba con rodillas flexionadas, despega los hombros del suelo contrayendo el abdomen y baja controlado.",
    ["No tires del cuello", "Exhala al subir", "Movimiento corto y controlado"],
    "side", "hip", 2000, "none",
    [dict(torso=-90, head=-92, thighF=135, shinF=-12, thighB=135, shinB=-12, armF=100, foreF=100, armB=100, foreB=100),
     dict(torso=-118, head=-125, thighF=135, shinF=-12, thighB=135, shinB=-12, armF=112, foreF=112, armB=112, foreB=112)], mat=True)

add("high_knees", "Rodillas al pecho", "BODYWEIGHT", ["Cardio", "Flexores de cadera", "Core"],
    "Corre en el sitio elevando las rodillas a la altura de la cadera y moviendo los brazos.",
    ["Mantente sobre la parte delantera del pie", "Tronco erguido", "Ritmo rápido"],
    "side", "hip", 800, "none",
    [dict(thighF=90, shinF=0, armF=-35, foreF=40, armB=40, foreB=120, lift=2),
     dict(thighB=90, shinB=0, armB=-35, foreB=40, armF=40, foreF=120, lift=2)])

# ================= MANCUERNAS =================
add("db_curl", "Curl de bíceps", "DUMBBELL", ["Bíceps", "Antebrazo"],
    "De pie con una mancuerna en cada mano, flexiona los codos llevando las mancuernas hacia los hombros y baja despacio.",
    ["Codos pegados al cuerpo", "Sin balancear el tronco", "Baja en 2-3 segundos"],
    "side", "footF", 2200, "dumbbells",
    [dict(armF=5, foreF=10, armB=5, foreB=10), dict(armF=10, foreF=155, armB=10, foreB=155)])

add("db_press", "Press de hombros", "DUMBBELL", ["Hombros", "Tríceps"],
    "Con las mancuernas a la altura de los hombros, empuja hacia arriba hasta extender los brazos y baja controlado.",
    ["Abdomen activo para no arquear la espalda", "No bloquees los codos de golpe"],
    "front", "hip", 2200, "dumbbells",
    [dict(armF=92, foreF=178, armB=92, foreB=178), dict(armF=168, foreF=176, armB=168, foreB=176)])

add("db_row", "Remo inclinado", "DUMBBELL", ["Espalda", "Bíceps", "Deltoides posterior"],
    "Inclina el tronco con la espalda recta y las rodillas ligeramente flexionadas; tira de las mancuernas hacia la cadera.",
    ["Junta las escápulas al subir", "Espalda neutra, no redondees", "Mirada al suelo"],
    "side", "footF", 2200, "dumbbells", [row_top, row_pull])

add("db_rdl", "Peso muerto rumano", "DUMBBELL", ["Isquiotibiales", "Glúteos", "Lumbares"],
    "Con las mancuernas delante de los muslos, lleva la cadera atrás bajando el tronco con la espalda recta y vuelve a subir.",
    ["Rodillas apenas flexionadas", "Mancuernas pegadas a las piernas", "Siente el estiramiento en los isquios"],
    "side", "footF", 2600, "dumbbells",
    [dict(), dict(torso=98, head=104, thighF=18, shinF=-4, thighB=18, shinB=-4)])

add("db_lateral_raise", "Elevaciones laterales", "DUMBBELL", ["Hombros"],
    "De pie con los brazos a los lados, eleva las mancuernas lateralmente hasta la altura de los hombros.",
    ["Codos ligeramente flexionados", "No subas por encima de los hombros", "Baja lento"],
    "front", "hip", 2200, "dumbbells",
    [dict(armF=12, foreF=16, armB=12, foreB=16), dict(armF=86, foreF=92, armB=86, foreB=92)])

add("db_lunge", "Zancada con mancuernas", "DUMBBELL", ["Cuádriceps", "Glúteos"],
    "Zancada estática sujetando una mancuerna en cada mano con los brazos estirados.",
    ["Torso vertical", "Baja la rodilla trasera cerca del suelo", "Cambia de pierna a mitad"],
    "side", "footF", 2400, "dumbbells", [lunge_top, lunge_bot])

add("db_floor_press", "Press de pecho en suelo", "DUMBBELL", ["Pectoral", "Tríceps"],
    "Tumbado boca arriba con rodillas flexionadas, empuja las mancuernas hacia el techo y baja hasta que los codos toquen el suelo.",
    ["Codos a 45° del cuerpo", "Pausa breve abajo", "Muñecas firmes"],
    "side", "shoulder", 2200, "dumbbells",
    [dict(torso=90, head=90, thighF=-135, shinF=12, thighB=-135, shinB=12, armF=180, foreF=180, armB=180, foreB=180),
     dict(torso=90, head=90, thighF=-135, shinF=12, thighB=-135, shinB=12, armF=-95, foreF=175, armB=-95, foreB=175)], mat=True)

# ================= KETTLEBELL =================
add("kb_swing", "Swing con kettlebell", "KETTLEBELL", ["Glúteos", "Isquiotibiales", "Core"],
    "Con la kettlebell entre las piernas, impulsa la cadera hacia delante de forma explosiva para balancearla hasta la altura del pecho.",
    ["El movimiento sale de la cadera, no de los brazos", "Espalda neutra", "Aprieta glúteos arriba"],
    "side", "footF", 1600, "kettlebell",
    [dict(torso=118, head=125, thighF=28, shinF=-14, thighB=28, shinB=-14, armF=-25, foreF=-25, armB=-25, foreB=-25),
     dict(armF=88, foreF=90, armB=88, foreB=90)])

add("kb_goblet_squat", "Sentadilla goblet", "KETTLEBELL", ["Cuádriceps", "Glúteos", "Core"],
    "Sujeta la kettlebell a la altura del pecho y realiza una sentadilla profunda manteniendo el tronco erguido.",
    ["Codos por dentro de las rodillas abajo", "Pecho alto", "Talones en el suelo"],
    "side", "footF", 2400, "kettlebell", [goblet_top, goblet_bot])

add("kb_deadlift", "Peso muerto con kettlebell", "KETTLEBELL", ["Glúteos", "Isquiotibiales", "Espalda"],
    "Con la kettlebell entre los pies, flexiona cadera y rodillas, agárrala con la espalda recta y ponte de pie empujando el suelo.",
    ["Cadera atrás al bajar", "Brazos estirados", "Bloquea con glúteos arriba"],
    "side", "footF", 2600, "kettlebell",
    [dict(), dict(torso=118, head=125, thighF=60, shinF=-25, thighB=60, shinB=-25)])

add("kb_press", "Press con kettlebell", "KETTLEBELL", ["Hombros", "Tríceps", "Core"],
    "Desde la posición de rack (kettlebell junto al hombro), empuja hacia arriba hasta extender el brazo. Alterna brazos por serie.",
    ["Muñeca recta", "Glúteos y abdomen apretados", "Bíceps cerca de la oreja arriba"],
    "side", "footF", 2200, "kettlebell",
    [dict(armF=15, foreF=172), dict(armF=176, foreF=178)])

add("kb_row", "Remo con kettlebell", "KETTLEBELL", ["Espalda", "Bíceps"],
    "Tronco inclinado con la espalda recta, tira de la kettlebell hacia la cadera con un brazo. Cambia de brazo a mitad.",
    ["Codo pegado al cuerpo", "No gires el tronco", "Controla la bajada"],
    "side", "footF", 2200, "kettlebell",
    [dict(row_base, armF=0, foreF=0, armB=40, foreB=10), dict(row_base, armF=-80, foreF=0, armB=40, foreB=10)])

# ================= BANDAS ELÁSTICAS =================
add("band_curl", "Curl con banda", "BAND", ["Bíceps"],
    "Pisa la banda con ambos pies y sujeta los extremos; flexiona los codos llevando las manos a los hombros.",
    ["Codos fijos", "Controla la vuelta de la banda", "Tensión constante"],
    "side", "footF", 2200, "band_feet",
    [dict(armF=5, foreF=10, armB=5, foreB=10), dict(armF=10, foreF=155, armB=10, foreB=155)])

add("band_row", "Remo con banda", "BAND", ["Espalda", "Bíceps"],
    "Pisa la banda, inclina el tronco con la espalda recta y tira de los extremos hacia la cadera.",
    ["Escápulas juntas arriba", "Espalda neutra", "Pausa al final del tirón"],
    "side", "footF", 2200, "band_feet", [row_top, row_pull])

add("band_press", "Press de hombros con banda", "BAND", ["Hombros", "Tríceps"],
    "Pisa la banda y, con las manos a la altura de los hombros, empuja hacia arriba hasta extender los brazos.",
    ["No arquees la espalda", "Baja controlando la tensión"],
    "front", "hip", 2200, "band_feet",
    [dict(armF=92, foreF=178, armB=92, foreB=178), dict(armF=168, foreF=176, armB=168, foreB=176)])

add("band_squat", "Sentadilla con banda", "BAND", ["Cuádriceps", "Glúteos"],
    "Pisa la banda y sujeta los extremos a la altura de los hombros; realiza una sentadilla manteniendo la tensión.",
    ["Pecho erguido", "Rodillas hacia fuera", "Sube empujando con los talones"],
    "side", "footF", 2400, "band_feet", [goblet_top, goblet_bot])

add("band_lateral_raise", "Elevaciones laterales con banda", "BAND", ["Hombros"],
    "Pisa la banda y eleva los brazos lateralmente hasta la altura de los hombros.",
    ["Codos ligeramente flexionados", "Sin impulso", "Baja lento"],
    "front", "hip", 2200, "band_feet",
    [dict(armF=12, foreF=16, armB=12, foreB=16), dict(armF=86, foreF=92, armB=86, foreB=92)])

add("band_glute_bridge", "Puente de glúteo con banda", "BAND", ["Glúteos", "Abductores"],
    "Con una mini banda por encima de las rodillas, eleva la cadera empujando las rodillas hacia fuera contra la banda.",
    ["Mantén la tensión lateral", "Aprieta glúteos arriba", "Baja sin apoyar del todo"],
    "side", "shoulder", 2200, "band_knees", [bridge_down, bridge_up], mat=True)

# ================= RUTINAS PREDEFINIDAS =================
def item(e, sets=3, work=40, rest=20):
    return dict(exerciseId=e, sets=sets, workSeconds=work, restSeconds=rest)

routines = [
    dict(id="preset_fullbody_bw", name="Cuerpo completo sin material",
         description="Rutina básica para todo el cuerpo usando solo tu peso.",
         restBetweenExercises=60, prepSeconds=10,
         items=[item("squat"), item("pushup"), item("lunge"), item("glute_bridge"), item("mountain_climbers", work=30, rest=30), item("plank", work=30)]),
    dict(id="preset_hiit_bw", name="HIIT cardio en casa",
         description="Intervalos intensos de 30 s con descansos cortos.",
         restBetweenExercises=45, prepSeconds=10,
         items=[item("jumping_jacks", 4, 30, 15), item("high_knees", 4, 30, 15), item("mountain_climbers", 4, 30, 15), item("squat", 4, 30, 15)]),
    dict(id="preset_core_bw", name="Core y abdomen",
         description="Trabajo de la zona media en 15 minutos.",
         restBetweenExercises=40, prepSeconds=10,
         items=[item("crunch", 3, 40, 20), item("plank", 3, 30, 20), item("mountain_climbers", 3, 30, 20), item("glute_bridge", 3, 40, 20)]),
    dict(id="preset_dumbbell", name="Fuerza con mancuernas",
         description="Cuerpo completo con un par de mancuernas.",
         restBetweenExercises=60, prepSeconds=10,
         items=[item("db_lunge", 3, 45, 30), item("db_floor_press", 3, 45, 30), item("db_row", 3, 45, 30),
                item("db_rdl", 3, 45, 30), item("db_press", 3, 40, 30), item("db_curl", 3, 40, 30)]),
    dict(id="preset_kettlebell", name="Kettlebell express",
         description="Potencia y fuerza con una sola kettlebell.",
         restBetweenExercises=60, prepSeconds=10,
         items=[item("kb_swing", 4, 30, 30), item("kb_goblet_squat", 3, 40, 30), item("kb_deadlift", 3, 40, 30),
                item("kb_press", 4, 30, 20), item("kb_row", 4, 30, 20)]),
    dict(id="preset_band", name="Bandas elásticas: tonificación",
         description="Rutina completa con bandas, ideal para viajes.",
         restBetweenExercises=45, prepSeconds=10,
         items=[item("band_squat"), item("band_row"), item("band_press"), item("band_glute_bridge"),
                item("band_curl", 3, 30, 20), item("band_lateral_raise", 3, 30, 20)]),
    dict(id="preset_mixed", name="Mixta: peso corporal + mancuernas",
         description="Combina ejercicios sin material con mancuernas.",
         restBetweenExercises=50, prepSeconds=10,
         items=[item("squat", 3, 40, 20), item("db_row", 3, 40, 20), item("pushup", 3, 30, 30),
                item("db_lateral_raise", 3, 30, 20), item("plank", 2, 40, 20)]),
]

out = dict(exercises=ex, routines=routines)
ids = {e["id"] for e in ex}
for r in routines:
    for it in r["items"]:
        assert it["exerciseId"] in ids, it
path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/assets/exercises.json")
with open(path, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print(len(ex), "ejercicios,", len(routines), "rutinas ->", path)
