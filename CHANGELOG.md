# Changelog

---

## [v1.3] — 2026-03-02
### Changed
- Removed subtitle from the start screen title

---

## [v1.2] — 2026-03-02
### Fixed
- **App crash on touch** — VIBRATE permission was missing from AndroidManifest.xml, causing a SecurityException on every tap

---

## [v1.1] — 2026-03-02
### Added
- **Start screen** — clean title, mode selector (Classic / Fixed Speed / Garbage coming soon), speed sub-picker (Slow / Med / Fast / Ultra), Play and Stats buttons
- **Settings panel** — slides in from the right with toggle switches for Ghost Piece, Grid Lines, Reduce Motion, High Contrast, and Vibration
- **Game over screen** — full stats overlay showing Score, Level, Lines Cleared, and Time Survived with animated score count-up; Restart and Menu buttons
- **Lock delay bar** — thin progress strip drawn under the active piece that drains as the lock timer counts down
- **Level up flash** — brief purple overlay with "LEVEL X" text on level increase
- **Screen shake** — triggers on 3+ line clears (stronger for Tetris); skipped when Reduce Motion is on
- **High contrast mode** — alternate fully-saturated color palette toggled from settings
- **Haptic feedback** — vibration on move, rotate, lock, line clear, hard drop, and game over; togglable in settings
- **Main menu navigation** — pause and game over screens both have a Menu button to return to the start screen
- **Fixed Speed mode UI** — speed selector renders when Fixed Speed mode is chosen (game logic uses selected delay)

### Changed
- **Info bar** — replaced flat debug-style score text with a three-column layout stacking SCORE / LEVEL / LINES; gear icon on the left, pause icon on the right, both in circle backgrounds
- **Hold and Next panels** — redesigned as card containers with borders and rounded mini-piece rendering
- **Board** — added a defined card container with subtle corner rounding
- **Cells** — top highlight edge and bottom shadow edge give blocks visual depth
- **Pause overlay** — added score summary, Restart button, and Main Menu button; removed orange color
- **Countdown overlay** — countdown number now uses accent color instead of plain white
- **Design system** — unified color tokens (BG, ACCENT, TXT_BRIGHT / MID / DIM), consistent card radius and border style across all panels

---

## [v1.0] — 2026-03-02
### Added
- Initial release
- Custom async passage engine — no Twine or external runtime
- 10×20 board, all 7 tetrominoes (I O T S Z J L)
- 7-bag randomizer via nextQueue
- Hold piece with one-hold-per-piece rule
- Ghost piece (white outline)
- SRS-style wall kicks with upward shifts for T-spin support
- Lock delay — 1.5 s grace period while moving or rotating on the floor (up to 15 resets)
- Soft drop landing — 350 ms fast-lock when no input follows
- Hard drop — instant lock on fast downward swipe
- Gravity — exponential speed curve (`550 × 0.84^(level−1)`, minimum 60 ms)
- Level progression — one level per 10 lines cleared
- Scoring — 100 / 300 / 500 / 800 × level for 1 / 2 / 3 / 4 line clears
- Line clear flash animation (250 ms white row before removal)
- Lock impact flash (130 ms white cell burst on piece lock)
- 3-2-1 countdown before game starts
- Pause / resume via tap on pause icon
- Restart button on pause screen
- Full-width board layout — Hold and Next moved to footer strip
- Touch gesture system — tap to rotate, swipe down to soft drop, fast swipe down for hard drop, swipe up for hold
- Stale position fix — lastX/lastY always updated in ACTION_MOVE so unblocking never jumps
- waitForLift flag — blocks drag carry-over when a new piece spawns mid-gesture
- lockedDuringGesture flag — prevents ACTION_UP swipe from hard-dropping the next piece after a lock
- Fast swipe suppresses horizontal movement to prevent accidental column shifts
