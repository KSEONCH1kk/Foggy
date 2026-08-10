# Foggy live debug

Debug работает от лица наблюдателя: войдите тем клиентом, у которого другой игрок не скрывается,
и выполните:

```text
/foggy debug <ник цели>
```

Каждые пять тиков обновляется action bar. `/foggy debug status` печатает полный снимок повторно,
`/foggy debug particles off` выключает только частицы, `/foggy debug off` завершает сессию.

После замены JAR нужен полный restart сервера, не `/reload` и не plugin-manager reload.
Старый `config.yml` можно оставить: отсутствующие параметры новой transparency-политики получают
встроенные значения. Чтобы увидеть и редактировать весь новый раздел, переименуйте старый конфиг и
дайте Foggy создать его заново либо скопируйте раздел `raycast` из нового default config.

## Как читать значения

| Поле | Значение |
|---|---|
| `FINAL` | Верхнеуровневая причина: `INVISIBLE` означает только hard hide (vanish/spectator), не `VANILLA_ENTITY` |
| `engineReason` | Решение, сохранённое VisibilityEngine на последнем entity-owned tick |
| `managed` | Пара находится в одном мире и внутри `visibility.radius-blocks` |
| `hidden` | Engine уже выполнил переход visible → hidden |
| `debounce` | Сколько последовательных optical-hide подтверждений накоплено |
| `bypass` | У viewer есть `foggy.bypass`; при `true` скрытия намеренно нет |
| `potion/flag/spectator` | Отдельные invisibility-сигналы цели |
| `mode` | `VANILLA_ENTITY` сохраняет entity/вещи/хиты; `PACKET_HIDDEN` полностью удаляет entity |
| `canSee` | Значение read-only `viewer.canSee(target)` для интеграции с vanish-плагинами |
| `optical` | Результат FOV + voxel raycast без учёта invisibility/bypass |
| `cameras` | Число проверенных camera poses; fallback включает first/back/front F5 и source margin |
| `exact` | Сколько poses пришло из свежего companion sample |
| `fovY` | Фактический диапазон вертикального FOV, использованный проверкой |
| `points` | Число hitbox samples с учётом partial-tick interpolation |
| `inFov` | Число camera/point комбинаций, прошедших frustum test до момента решения |
| `traced` | Число выполненных block rays до момента решения |
| `blocked` | Сколько этих rays попали в voxel shape блока |
| `BLOCK` | Материал/координаты blocker, optical mode, outline bounds, точное число OUTLINE/collision sub-boxes и hit |
| `state` | Полный block state (`facing`, `half`, `shape`, `waterlogged`, connections и т. п.) |
| `PASS` | Первая OUTLINE-форма, пропущенная как `TRANSPARENT_PASS` или `CUTOUT_PASS` |
| `packet.hiddenId` | PacketEvents listener сейчас подавляет пакеты entity id для viewer |
| `observedTracked` | Foggy видел обычный server spawn этого id |
| `clientKnown` | По состоянию Foggy сущность сейчас существует на клиенте |
| `paperTracked` | Paper entity tracker включает viewer для этой цели |
| `cache.pairHit/pairMiss` | Попадания/промахи кэша неизменившегося optical-решения пары |
| `cache.cells/cellHit/refresh` | Число sparse block cells, повторных чтений и холодных/validation обновлений |
| `cache.fallback` | Должно быть `0`; больше нуля означает консервативный локальный full-cube fallback из-за несовместимого NMS bridge |

Для полностью скрытой стеной цели ожидается:

```text
bypass=false managed=true
optical=OCCLUDED engineReason=OCCLUDED hidden=true
packet[hiddenId=true, ..., clientKnown=false, paperTracked=true]
```

Для цели, которую удерживает один открытый луч, вывод содержит дополнительную строку:

```text
VISIBLE ray: camera=FALLBACK:THIRD_BACK#... pos=(...) -> target=(...)
```

Она показывает конкретную fallback/companion камеру и hitbox point, из-за которых скрывать игрока
нельзя. Это позволяет отличить настоящий просвет около стены от консервативной F5-эвристики.

## Цвета частиц

- синий — fallback camera origins;
- голубой — точная companion camera;
- жёлтый — forward-вектор основной камеры;
- серый — target hitbox samples для `VISIBLE`/`OUTSIDE_FOV`;
- красный — hitbox samples и representative blocked rays при occlusion;
- оранжевый — точное место попадания ray в voxel shape блока;
- золотые рёбра — каждый AABB фактического OUTLINE `VoxelShape.toAabbs()` первого blocker;
- фиолетовый — место пересечения прозрачной OUTLINE-формы, через которую луч продолжился;
- зелёный — первый чистый ray, который делает итог `VISIBLE`.

Все частицы отправляются только debug-viewer и не видны другим игрокам.

## Частые причины «не скрывает»

1. `bypass=true`: снимите явно выданное `foggy.bypass`. В актуальном JAR оно больше не выдаётся OP
   автоматически.
2. `managed=false`: увеличьте radius или поместите игроков в один мир/ближе друг к другу.
3. `optical=VISIBLE`: осмотрите зелёный ray и имя камеры. Он показывает реальный открытый sample или
   fallback F5 pose, с которого цель потенциально видна.
4. `PASS=... mode=TRANSPARENT_PASS/CUTOUT_PASS`: луч пересёк форму стекла, двери, забора,
   калитки или другого conservative cutout, но намеренно продолжился. Перенесите материал в
   `opaque-material-overrides`, если на этом сервере он должен скрывать.
5. `hidden=true`, но `packet.hiddenId=false`: packet-state рассинхронизирован; приложите полный
   `/foggy debug status` и лог PacketEvents.
6. `paperTracked=false`: Paper сам не отправляет эту сущность viewer; `clientKnown=false` в таком
   состоянии нормален.
7. `optical=REGION_UNOWNED`: текущий Folia region не владеет всем коридором луча. Foggy намеренно
   оставляет цель видимой, чтобы не читать чужие chunks с неправильного tick thread.
