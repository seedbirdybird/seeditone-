# Seeditone (Fabric 1.21.11, client-side)

Build:   gradle wrapper && ./gradlew build   ->  build/libs/seeditone-1.0.0.jar
         (needs JDK 21; Loom 1.14 wants a recent Gradle, e.g. 9.x)
Open GUI: Right Shift, or /seed
Builder:  put .schem files in .minecraft/schematics/, open Builder tab, click a file
          or  /seed build <name>   |   /seed stop
Theme:    Settings tab -> pick a color entry, drag the picker. Saved to config/seeditone.properties

Limits: Sponge .schem only, no rotation-dependent states, crude walk logic, no scaffolding.

Modes (Builder tab or /seed mode none|assist|semi|full):
  None    - builder off
  Assist  - places blocks within reach while YOU walk around (no walking, no buying)
  Semi    - walks + places by itself, you supply the materials
  Full    - Semi + buys missing items with a command you set:  /seed buycmd <template>
            template placeholders: {item} {amount}   (max 3 buy attempts per item, then it skips it)
Importing: drop .schem in .minecraft/schematics, click it in the Builder tab. Chat prints the materials list.
