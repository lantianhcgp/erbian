#!/data/data/com.termux/files/usr/bin/bash
# 单 heredoc 全设备内执行的面板发送（避开 rish 多轮往返的时序/截断坑）
TEXT="${1:?text}"
cat <<EOF | timeout 180 "$HOME/rish/rish_t" 2>&1
am force-stop com.lantianhcgp.erbian
sleep 2
am start -a android.intent.action.ASSIST -n com.lantianhcgp.erbian/.ui.MainActivity > /dev/null
sleep 6
uiautomator dump /sdcard/p1.xml > /dev/null 2>&1
B=\$(cat /sdcard/p1.xml | grep -o 'text="说点什么"[^/]*bounds="[^"]*"' | grep -o '\\[[0-9,]*\\]\\[[0-9,]*\\]' | head -1)
if [ -z "\$B" ]; then echo NO_INPUT_FIELD; exit 3; fi
T=\${B#\\[}; X1=\${T%%,*}; T=\${T#*,}; Y1=\${T%%\\]*}; T=\${T#*\\[}; X2=\${T%%,*}; T=\${T#*,}; Y2=\${T%%\\]*}
CX=\$(( (X1+X2)/2 )); CY=\$(( (Y1+Y2)/2 ))
echo "input_at \$CX \$CY"
input tap \$CX \$CY
sleep 1
input text \"$TEXT\"
sleep 1
uiautomator dump /sdcard/p2.xml > /dev/null 2>&1
B2=\$(cat /sdcard/p2.xml | grep -o 'content-desc="发送"[^/]*bounds="[^"]*"' | grep -o '\\[[0-9,]*\\]\\[[0-9,]*\\]' | head -1)
if [ -z "\$B2" ]; then echo NO_SEND_BTN; exit 4; fi
T=\${B2#\\[}; X1=\${T%%,*}; T=\${T#*,}; Y1=\${T%%\\]*}; T=\${T#*\\[}; X2=\${T%%,*}; T=\${T#*,}; Y2=\${T%%\\]*}
SX=\$(( (X1+X2)/2 )); SY=\$(( (Y1+Y2)/2 ))
echo "send_at \$SX \$SY"
input tap \$SX \$SY
sleep 3
echo DONE
EOF
