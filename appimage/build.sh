#!/bin/bash

HERE="$(dirname "$(readlink -f "${0}")")"

pushd "$HERE/../build" >/dev/null

rm -rf AppDir
mkdir -p AppDir/usr
mkdir -p AppDir/opt/jrommanager

cp -rp jpackage/JRomManager/* AppDir/opt/jrommanager/
cp -p jpackage/JRomManager/lib/JRomManager.png AppDir/
cp -p "$HERE/jrommanager.desktop" AppDir/

cp -p "$HERE/AppRun" AppDir/
chmod +x AppDir/AppRun

rm -f *.AppImage
rm -rf squashfs-root
wget -q https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage
chmod +x appimagetool-x86_64.AppImage
# FUSE-free: GitHub ubuntu-24.04 runners ship no libfuse.so.2 and AppImages need
# FUSE to run, but --appimage-extract works without mounting, so extract the tool
# and run it from squashfs-root instead of executing the AppImage directly.
./appimagetool-x86_64.AppImage --appimage-extract >/dev/null
./squashfs-root/AppRun -n AppDir
rm -rf squashfs-root

popd >/dev/null
