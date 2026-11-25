# Mixamo 3D Studio (Android + Three.js)

Aplicativo Android híbrido em tela cheia que integra o portal **Mixamo** (com injeção automática do script de toque-para-mouse e captura de downloads `.fbx`) diretamente a um **Mundo Aberto 3D em Three.js** para testar personagens e movimentos salvos no aparelho.

## Como gerar o APK (2 formas)

### 1. Direto pelo GitHub Actions (sem precisar de PC)
1. Crie um repositório no GitHub e envie todos os arquivos deste ZIP.
2. Abra a aba **Actions** no repositório.
3. O workflow **Build Mixamo 3D Studio APK** será executado automaticamente (ou clique em **Run workflow**).
4. Ao finalizar, baixe o artefato `Mixamo3DStudio-debug-apk` e instale o `app-debug.apk` no celular.

### 2. Pelo Android Studio
1. Extraia o ZIP e abra a pasta raiz no Android Studio.
2. Aguarde o Gradle sincronizar e clique em **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.

## Fluxo dentro do App
1. Toque na engrenagem (`⚙️`) no canto superior direito -> **Abrir Mixamo em Tela Cheia**.
2. Faça login na sua conta Mixamo e suba seu personagem (`.obj`, `.fbx` ou `.zip`).
3. No Auto-Rigger, toque no botão flutuante **👆 Rolagem -> 🖱️ Mouse Ativo** para arrastar os marcadores com o dedo.
4. Baixe a primeira animação como **FBX Binary + With Skin** e salve como **Novo Personagem**.
5. Baixe quantas animações extras quiser como **Without Skin** e adicione ao personagem salvo.
6. Toque em **🌍 Mundo 3D** para testar o personagem com joystick, pulo, corrida e botões de movimentos extras.
