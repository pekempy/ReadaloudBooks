# ReadAloud Books

An Android application for audiobook and eBook consumption with a focus on high-quality audio playback and Material You design.

## Images

<details>
<summary><strong>Click to expand screenshots</strong></summary>
<br>

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/c56de205-5fa4-456c-9095-58791a745021" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Login Screen
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/829a922b-6804-493f-930e-6f23f49d5d33" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Home Screen (now Shelf)
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/0f074336-4c54-4737-93bd-4cdb1b254821" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Series View
    </td>
  </tr>

  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/e8629f42-9798-4014-b9ce-8f96546d4c2c" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Book Details
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/5a5645da-d77c-46b3-b933-2ea75a5d6237" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Readaloud View
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/60cec593-a8a6-4489-994e-321ce0572eb7" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Download viewer / queueing
    </td>
  </tr>

  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/29ecda12-347e-4cd1-aa44-842bede3b9ab" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Storage management
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/5a4c1838-f748-49a1-a43e-ebee4ba2e26e" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Settings page
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/f52e0201-9b08-4388-a839-5b1d2a110958" width="230" style="border:1px solid #ccc; border-radius:12px;"><br/>
      Theming
    </td>
  </tr>
</table>

</details>





## Features

### Audiobook Playback
- **Format Support**: Supports M4B, Atmos, and EAC3.
- **Transcoding**: Integrated FFmpeg streaming proxy for real-time transcoding of unsupported codecs.
- **Chapter Detection**: Native and probed chapter extraction.
- **Offline Support**: Local file downloads and storage management.
- **Playback Controls**: Variable speed (0.5x - 2.0x), sleep timer, and background playback.

### eBook Reader
- **EPUB Loading**: Fast loading and smooth scrolling of EPUB files.
- **Customization**: Support for adjustable font sizes, font families, and multiple reader themes (White, Sepia, Dark, OLED).
- **ReadAloud Sync**: Concurrent audio playback and text highlighting.

### UI & Design
- **Material You**: Full support for Material 3 and Dynamic Color on Android 12+.
- **Jetpack Compose**: Entirely declarative UI implementation.

## Getting Started

### Prerequisites
- Android device running API 24+ (Android 7.0).
- A Storyteller instance for book metadata and content.

### Build & Release
This repository uses GitHub Actions for automated releases. Push a tag starting with `v` (e.g., `v1.0.0`) to trigger the build and release process.

## Roadmap
- [x] **Media Sync**: Fix syncing across ReadAloud / Audiobook / eBook, to try and keep them all together.
- [x] **Cloud Sync**: Synchronize progress with your Storyteller instance.
- [ ] **Metadata Editor**: Edit book metadata and cover art within the app.
- [ ] **Grid Customization**: Adjustable grid sizes in library views.

## License
Distributed under the AGPL3 License.
