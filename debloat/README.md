# TCL debloat

`debloat.sh` uses `adb shell pm disable-user`. That is reversible. `restore` runs `pm enable`. Nothing is uninstalled.

The default list is the short set used on a TCL Google TV for this project:

- `com.tcl.tv.plus`
- `com.tcl.t_solo`
- `com.tcl.ui_mediaCenter`
- `com.tcl.dashboard`
- `com.tcl.hearaid`
- `com.tcl.usercenter`

`com.tcl.t_solo` and `com.tcl.ui_mediaCenter` are not in every public catalog. The script prints that warning. Confirm them with `pm list packages` on your TV before you answer YES.

`debloat/tcl-google-tv.txt` is a longer catalog with `safe`, `optional`, and `keep` tiers. `--tier safe` disables only the safe tier. The script never reads the keep tier.

Live TV (`com.tcl.tv`) and the input menu (`com.tcl.suspension`) stay enabled. Family Home hides them in the app row and the All apps grid. Hiding is not the same as disabling.

Do not disable `com.google.android.apps.tv.launcherx` until Family Home is already the home screen (`launcher/family-home.sh home`) and you have opened it. `family-home.sh restore` turns the stock launchers back on.

```bash
debloat/debloat.sh disable 192.0.2.10:5555
debloat/debloat.sh restore 192.0.2.10:5555
debloat/debloat.sh disable --tier safe 192.0.2.10:5555
```

Replace `192.0.2.10:5555` with the TV address from `config/family.json`. The script asks you to type YES.
