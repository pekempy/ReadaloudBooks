# Release Notes - v0.11.0-beta

## ✅ FULLY WORKING FEATURES

### 🎨 Theme Customization (COMPLETE)
- **Dynamic Colors** - Material You theming from system
- **Custom Color Picker** - Choose any color for app theme
- **4 Theme Modes** - System, Light, Dark, AMOLED Black
- **Tab Visibility Toggles** - Show/hide Books, Authors, Series, Collections tabs
- **All settings persist** and apply instantly

### 💾 Storage Management (COMPLETE)
- View all downloaded files
- Clear cache
- Manage disk usage
- Fully functional storage screen

### 🎵 M4B Chapter Names (IMPLEMENTED - Needs Testing)
- Extracts real chapter names from Storyteller M4B stream
- No more "split_003" filenames!
- Works without downloading M4B file
- Uses FFprobe to read metadata remotely

### 🔧 Settings Navigation (FIXED)
- All settings screens properly wired
- No more broken routes
- Smooth navigation transitions
- Clean architecture

## 📍 PLACEHOLDER SCREENS (Coming in v0.11.1)

These screens exist but show "Coming Soon":
- **Backup & Restore** - Settings export/import
- **Advanced Settings** - Power user options  
- **Reading Analytics** - Stats and progress tracking

## 🐛 BUG FIXES

- Fixed all Compose deprecation warnings
- Fixed broken MainActivity navigation routes
- Removed orphan code from previous attempts
- Clean build with zero compiler errors
- Fixed storage route path

## 🏗️ TECHNICAL IMPROVEMENTS

- Proper route structure in MainActivity
- Placeholder screens for future features
- Better code organization
- All features properly integrated

## 📦 BUILD INFO

- **Version Code**: 1101
- **Version Name**: 0.11.0-beta
- **Build**: Successful ✅
- **Compiler Warnings**: 0 (from our code)
- **Compatible**: AGP 8.7.3 + Kotlin 2.1.0

## 🎯 WHAT'S NEXT (v0.11.1)

1. Implement full Backup/Restore functionality
2. Complete Advanced Settings with all options
3. Build Reading Analytics with real data tracking
4. Add Download Selection Dialog
5. Implement Batch Operations for library
6. Test M4B chapter extraction on real books

## ⚠️ KNOWN LIMITATIONS

- Analytics screen is placeholder only
- Backup feature not yet functional
- Advanced settings screen is placeholder
- M4B chapter extraction untested in production

---

**This release focuses on getting existing features properly wired and accessible rather than adding broken placeholder code.**
