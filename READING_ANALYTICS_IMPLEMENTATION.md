# Reading Analytics Implementation (v0.11.1)

## Overview
Complete implementation of reading analytics feature including session tracking, data persistence, statistics calculation, and UI display.

## Components Implemented

### 1. Data Models (`data/ReadingSession.kt`)
- **ReadingSession**: Tracks individual reading sessions (bookId, startTime, endTime, durationMs, bookType)
- **ReadingStats**: Aggregates statistics (totalReadingTimeMs, currentStreak, averageSessionMs, readingByDay, readingByBook)
- **BookStats**: Per-book statistics (totalTimeMs, progress, lastRead)

### 2. Data Persistence (`data/ReadingStatsRepository.kt`)
- Custom SQLite database with reading_sessions table
- Methods:
  - `insertSession()`: Logs reading sessions to database
  - `getStats()`: Calculates comprehensive reading statistics
  - `getSessionsForBook()`: Retrieves sessions for specific book
  - Daily and per-book aggregation queries

### 3. ViewModel (`ui/analytics/ReadingAnalyticsViewModel.kt`)
- Manages analytics state
- Loads stats from repository
- Exposes stats via StateFlow for UI binding

### 4. Session Tracking Integration (`ui/player/ReadAloudAudioViewModel.kt`)
- Added session lifecycle management:
  - `sessionStartTime`: Tracks when reading starts
  - `startSession()`: Called when playback begins
  - `endSession()`: Called when playback pauses/ends
  - Sessions < 10 seconds are filtered out
- Integrated with Player.Listener:
  - `onIsPlayingChanged()`: Triggers start/end
  - `pause()`: Ends session
  - `onCleared()`: Cleanup on ViewModel destruction

### 5. UI Screen (`ui/settings/PlaceholderScreens.kt`)
Replaced placeholder with real ReadingAnalyticsScreen featuring:
- **Summary Cards**:
  - Total Time: Formatted reading time
  - Current Streak: Days with reading activity
  - Avg Session: Average session duration
  
- **Weekly Chart**:
  - Last 7 days of reading with bar visualization
  - Progress bars with max scaling
  - Duration labels

- **Books List**:
  - Top books by reading time
  - Title and total time per book

- **Empty State**: Encourages users to start reading

### 6. Navigation
- Route: `"analytics"` in MainActivity NavHost
- Accessible via navigation menu
- Smooth slide transitions

## Technical Details

### Database Design
```sql
CREATE TABLE reading_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id TEXT NOT NULL,
    start_time INTEGER NOT NULL,
    end_time INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    book_type TEXT NOT NULL
);

CREATE INDEX idx_book_id ON reading_sessions(book_id);
CREATE INDEX idx_start_time ON reading_sessions(start_time);
```

### Statistics Calculation
- **Total Time**: SUM(duration_ms) across all sessions
- **Current Streak**: Count consecutive days with reading activity
- **Avg Session**: AVG(duration_ms) for sessions > 10 seconds
- **Daily Stats**: Grouped by date for last 30 days
- **Per-Book Stats**: Aggregated by bookId with MAX(start_time) for recency

### Session Minimum Duration
Sessions shorter than 10 seconds are filtered to avoid logging accidental pauses/taps.

### Threading
- Session insertion and stats calculation run on Dispatchers.IO
- UI updates via StateFlow collection

## Files Modified/Created

### Created:
- `data/ReadingSession.kt` - Data models
- `data/ReadingStatsRepository.kt` - Database layer
- `ui/analytics/ReadingAnalyticsViewModel.kt` - ViewModel

### Modified:
- `ui/player/ReadAloudAudioViewModel.kt` - Session tracking
- `ui/settings/PlaceholderScreens.kt` - Real analytics UI

### Navigation:
- `MainActivity.kt` - Route already configured (no changes needed)

## Verification

All components verified:
✓ Data models created
✓ Repository implemented
✓ ViewModel created
✓ Session tracking integrated
✓ Analytics screen implemented
✓ Route configured
✓ No compilation errors related to reading analytics

## Usage Flow

1. User opens a book and starts reading
   - `onIsPlayingChanged(true)` → `startSession()`
   
2. Reading session active
   - `sessionStartTime` tracks elapsed time
   
3. User pauses/stops reading
   - `onIsPlayingChanged(false)` or `pause()` → `endSession()`
   - If duration > 10 seconds, session logged to database
   
4. User navigates to Analytics
   - `ReadingAnalyticsViewModel.loadStats()`
   - Queries database for aggregated statistics
   - UI renders summary cards, charts, and book list
