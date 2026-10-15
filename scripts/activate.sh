#!/data/data/com.termux/files/usr/bin/bash
# 真机激活（经 rish 以 shell 执行）: activate.sh <apk在/data/local/tmp的文件名>
# 步骤: 安装 -> 授权限 -> 写唤醒三键 -> 写上行 secret -> 首启
set -e
APK_NAME="${1:?apk name under /data/local/tmp}"
PKG=com.lantianhcgp.erbian
COMP=$PKG/$PKG.voice.AssistInteractionService
SECRET=$(cat /data/data/com.termux/files/home/.hermes/secrets/erbian_webhook_secret.txt)

echo "== install =="
pm install -r "/data/local/tmp/$APK_NAME"

echo "== grant =="
pm grant $PKG android.permission.READ_CALENDAR 2>&1 || true
pm grant $PKG android.permission.WRITE_CALENDAR 2>&1 || true
pm grant $PKG android.permission.POST_NOTIFICATIONS 2>&1 || true

echo "== MIUI background-popup op (10021) — package update resets it == "
cmd appops set com.lantianhcgp.erbian 10021 allow || true
echo "== wake settings =="
settings put system long_press_power_key none
settings put global power_button_long_press 5
settings put secure assistant "$COMP"
settings put secure voice_interaction_service "$COMP"
settings get secure assistant

echo "== roles (best effort) =="
cmd role add-role-holders android.app.role.ASSISTANT "$PKG" 2>&1 || true
cmd role add-role-holders android.app.role.VOICE_INTERACTION "$PKG" 2>&1 || true
cmd role get-role-holders android.app.role.ASSISTANT 2>&1 || true
cmd role get-role-holders android.app.role.VOICE_INTERACTION 2>&1 || true

echo "== uplink secret via run-as =="
run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/erbian.xml' <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="uplink_secret">$SECRET</string>
</map>
EOF

echo "== first launch =="
am start -n $PKG/.ui.MainActivity
echo "ACTIVATE_DONE"
