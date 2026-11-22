# OpenMoHAA для Android

Неофициальный порт [OpenMoHAA](https://github.com/openmoh/openmohaa), открытого движка
**Medal of Honor: Allied Assault** (а также Spearhead и Breakthrough), на Android.

В комплекте нет файлов игры. Нужна своя лицензионная копия, например
[Medal of Honor: Allied Assault War Chest на GOG](https://www.gog.com/en/game/medal_of_honor_allied_assault_war_chest).

## Установка

1. Скачайте последний `.apk` на странице **[Releases](../../releases)** и установите его.
   Android попросит разрешить установку из неизвестного источника.
2. Скопируйте на телефон папку, куда игра установлена на ПК. Нужны папки `main`,
   а для дополнений ещё `mainta` и `maintt`.
3. Запустите **OpenMoHAA**, нажмите **«Импортировать файлы игры»** и выберите эту папку.
   Есть и другой способ: скопируйте папки `main`, `mainta` и `maintt` по USB в
   `Android/data/com.openmohaa.android/files/`.
4. Выберите игру и нажмите **«Играть»**.

## Управление

| Элемент | Действие |
|---|---|
| Левая половина экрана | Ходьба (плавающий стик) |
| Правая половина экрана | Обзор (свайп) |
| FIRE | Стрельба; не отпуская кнопку, можно вести пальцем для прицеливания |
| ALT | Альтернативная атака / удар |
| JUMP, CROUCH | Прыжок; присесть (переключатель) |
| RELOAD, USE | Перезарядка; использовать |
| <LEAN, LEAN> | Выглянуть из-за угла |
| WPN-, WPN+ | Смена оружия |
| SCORE, MENU, KBD, ~ | Счёт; меню; клавиатура; консоль |

В меню касания работают как мышь. Поддерживаются геймпады.

Настройки в консоли (`~`):

- `in_touchSens 1.0`: чувствительность обзора
- `in_touchSensY 0.8`: множитель для вертикали
- `in_touchInvertY 0`: инверсия вертикали
- `in_touchAlpha 0.25`: прозрачность кнопок
- `in_touchScale 1.0`: размер кнопок
- `in_touch 0`: выключить сенсорное управление (например, при игре на геймпаде)

## Графика

- **Классический рендер (по умолчанию).** Оригинальный рендерер OpenMoHAA, который
  работает на OpenGL ES 2 через [gl4es](https://github.com/ptitSeb/gl4es).
- **OpenGL ES (экспериментальный).** Рендерер OpenGL2 из OpenMoHAA, работает на GLES
  напрямую. В самом OpenMoHAA он ещё в разработке, поэтому возможны ошибки графики.

Если на телефоне глючит графика (например, на Pixel 10 с GPU PowerVR), можно попробовать
включить ANGLE (OpenGL ES поверх Vulkan) для приложения: «Для разработчиков» →
«Настройки ANGLE» (ANGLE Preferences) → OpenMoHAA → `angle`.

## Сборка

APK собирается автоматически через GitHub Actions (`.github/workflows/android.yml`).
Для локальной сборки нужны Android SDK, NDK r27, cmake ≥ 3.25, ninja, flex и bison:

```sh
export ANDROID_NDK_HOME=/path/to/ndk
./android/build_native.sh arm64-v8a
cd android && gradle assembleDebug
```

## Что внутри

- `patches/openmohaa-android.patch`: изменения в OpenMoHAA (GPLv2) для Android. При сборке
  исходники движка скачиваются с фиксированного коммита, и к ним применяется этот патч:
  - `code/sdl/sdl_touch.c`: сенсорное управление;
  - `code/sys/sys_main.c`: пути и загрузка модулей на Android;
  - `code/sdl/sdl_glimp.c`: поддержка gl4es;
  - `cmake/platforms/android.cmake`: настройки сборки под Android.
- `android/`: проект Android-приложения (лаунчер, импорт файлов, SDL2 activity) и
  скрипты сборки. OpenMoHAA, SDL2, OpenAL Soft и gl4es скачиваются при сборке в `~/openmohaa-android-deps`
  (`android/fetch_deps.sh`).
- `android/debug.keystore`: публичный отладочный ключ подписи. Он нужен, чтобы
  новые сборки устанавливались поверх старых и не удаляли импортированные файлы.
  Для этого неофициального проекта этого достаточно.

## Лицензия

GPLv2, как и у OpenMoHAA. Medal of Honor — торговая марка Electronic Arts. Проект не
связан с EA и не содержит файлов игры.
