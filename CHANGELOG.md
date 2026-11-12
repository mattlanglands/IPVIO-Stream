# Changelog

## Unreleased
- TV guide: the time axis stays still when moving Up/Down to another channel; it only scrolls when the selected show is out of view (before, every show was centred, so the whole grid jumped back and forth)
- TV guide: channels without archive keep the shows of the last 6 hours (before: 1 hour), so scrolling back shows what ran earlier instead of empty rows
- Internal: player helper code split from Media3 (no user-visible change).
- Search: the query box above the app keyboard can now be selected; OK or a tap opens the device keyboard for typing
- TV guide in the player: a playback error box (for example "check username, password or subscription") no longer sits on top of the guide
- TV guide search: channel hits show the show running right now with its times (also for providers without XMLTV, via the guide query)
- Live TV: when a playlist has no live channels, the empty list points to Settings > Playlists (content mode)
- Movies, series and home: when a playlist has none (content mode "live TV only"), the empty page points to Settings > Playlists
- Live TV: the tab always opens on "Start" (home) instead of the last category or "Alle Sender"; the hint above also shows there
- Search: Back from a selected result goes to the input (keyboard); Back again opens the sidebar, also when the Search entry is hidden in the menu (the default) (T-013)
- Backup: dual view, new-episode marks, imported lists and the resume log are now part of the backup export and restore (T-006)
- Playlist refresh defaults to "daily" instead of "at every start" for users who never chose a mode; the first start still loads once (T-009, refresh default)
- Live TV, movies and series: sort categories by provider order, A-Z or an own order; move categories with the menu key or a long OK (T-011)
- Player: search subtitles online (OpenSubtitles and SubDL) under Audio & subtitles; the chosen subtitle is attached while playing and remembered per title. Needs your own API keys (Settings > API keys) (PR #9)
**Playlists (T-009)**
- Xtream: any number of backup server addresses per login (same username and password). If the main server stops answering, the app switches to the next one by itself, for the playlist, TV guide, catch-up and streams
- Each server shows whether it is reachable or since when it is not; the playlist row in Settings > Playlists lists servers that are down
- Backup servers are included in the backup export and import
**Playlists (Stalker)**
- Stalker/Ministra portals load movie and series categories on demand: the first load fetches all channels plus the newest page of each category, further titles come when a category is opened, previewed or scrolled (PR #7)
- Stalker: movies with their own files (`has_files`) play, relative covers are shown, channels in movie-named genres stay in Live TV (PR #7)
- Stalker: a portal that only echoes the requested command no longer yields a broken play address, and a portal that ignores `movie_id` no longer plays the first movie of a category instead; movie and series categories with the same name keep separate title counts (follow-up to PR #7)
- Stalker: movies and episodes play on more portals: several `create_link` request forms, addresses from `url`, relative paths or `play_token`, file lookup for episodes, a new login when the portal rejects the session, a short wait on HTTP 429; when nothing plays, the error shows a masked summary of the portal's answer (Stalker part of PR #19)
- Stalker: a `create_link` answer with an error (e.g. `nothing_to_play`) or a bare video club command no longer counts as a play address, so the file lookup still runs; a file list that is really a whole category no longer starts another title; after a timeout, "429 Too Many Requests" or the account's stream limit no further request forms are sent; live channels log in again once after a failed start, as before PR #19 (follow-up to PR #19)

**Playlists (T-008)**
- Xtream logins loaded via the API: the loading screen now counts movies and series while they download. Before, it stood still at the size of the channel list (often "256 KB") until everything was there

## 1.3
**Playlists and loading**
- Large playlists load faster: reading, unpacking and parsing now overlap, and the category and quality checks are cheaper
- Providers whose certificate does not match their address ("certificate is for ...") no longer fail: for this one error the app tries the same address over http once and remembers the host. Every other certificate error is still refused
- Stalker portals: optional serial number and device IDs (as in TiviMate or STB Emu) for providers that ask for them; empty means the old behaviour
- The setup screen is more compact (everything visible at once, no animated scrolling) and offers "Continue without playlist" on the first start

**Playback**
- Short network drops are retried up to 6 times with a growing pause before an error is shown (no retries where they cannot help: wrong login, subscription or address)
- After a stall, playback resumes only with a few seconds in the buffer, so it does not stall again right away

**Watched marks and search**
- Mark a movie, season or whole series as watched or unwatched (also per episode: hold OK or the menu key); single titles can be removed from "Recently watched"
- Option "Device keyboard in search" in the settings (other languages, voice input, paste)

**Updates**
- "Update available" dialog at app start with one button to download and install, also on the "add playlist" screen. "Later" hides it until the next start

## 1.2
**Removed**
- Optional backup to Google Drive (sign-in with a code). Planned again for 1.4 as LiveSync. The old code is in tag V1.1

**Stalker portals**
- More robust login (several portal address variants, tolerant JSON), fallback paging for channel lists, clearer error messages
- Series support (own series area and series inside VOD); the second stream can play Stalker channels

**Sports hub (rebuilt)**
- Sports sidebar, day tabs, game cards with scores (unofficial ESPN data, can be switched off), matching of games to your channels, leagues on and off
- Favourite teams, alerts when a favourite team plays or scores (with optional delay), mini scoreboard in a corner (position, size, logos or names, game time)
- Long press a channel to correct which channel belongs to a game

**Live TV**
- New info bar when zapping (logo, LIVE, number, channel, quality, show, time left, progress, next)
- Genre filters (Sport, Movies, Documentaries, Kids, News, Music) and a "Start" page
- Quality and frame rate shown in the channel list (learned while watching)
- Hide live categories (favourite key in Live TV, "Hidden" at the bottom of the list)
- Ignore groups by country or language tag (Settings > Genres & categories), effective at once

**Interface**
- Seven ready-made designs, one click each
- Clock next to the profile icon (can be switched off)
- "Fit to your screen": margin for sides and top/bottom (0-10 %)
- Own on-screen keyboard in search with live results while typing
- Switch the playlist from the header when there are two or more
- On phones the app stays in landscape; the "add playlist" screen and the player button row can be swiped

**Boomer mode**
- Mode for older relatives, set up by someone else (Settings > Boomer Mode, PIN of the parental control): big channel tiles, only left/right zaps in the player, hidden exit (hold Back for 10 seconds, then PIN), start after boot
- Look is adjustable with a live preview (card size, text size, highlight colour, border, animations, logos) and the tiles can be chosen by hand

**Backup and search**
- Backup can be saved to and loaded from a file (Settings > Backup); more settings are included
- Phone search shows "Continue watching" when the search is empty
- Playlist download identifies itself as a common player (some providers slowed unknown clients)

## 1.1
**Updates and your data**
- In-app updates from GitHub Releases: the app checks once a day and shows "Update available" in the bell; one click downloads and installs
- Release signing via `keystore.properties` (see docs/RELEASE.md) so updates install over the old version and keep history, favorites and settings

**Playlists**
- Xtream: if `get.php` fails or returns no playlist (e.g. custom error codes like 884), the app loads the account through `player_api.php` automatically
- Several playlists at the same time: "Also show" in Settings > Playlists; movies and series merged (duplicate movies once, same series as one series with several sources), "Source" filter in Movies/Series, source switch in Live TV
- Stalker/Ministra portals (portal address + MAC): live channels and movies
- M3U file from the device (file picker) or uploaded from the phone page

**Live TV**
- Rewind live TV (optional on-device buffer, 256 MB – 2 GB), pause without missing anything, "Back to live"
- Record the current show (from the buffer, no second connection), storage internal or USB, recordings list in Live TV
- Endless reconnect for live streams, automatic reconnect when the server ends a stream
- New "Sports" section: sports live now and coming up (from the TV guide), all sports channels

**Player**
- MPEG-DASH (.mpd) streams; unsupported formats show a message instead of crashing

**Interface**
- View options moved from the header to Settings > View, with a live preview of card size and artwork
- Sidebar on the left, right or top; icons only or always with text
- Phones: landscape by default, tap for controls, double-tap left/right to skip

**Sports, playlists, second stream**
- Sports hub with filters: sport (football, basketball, tennis, F1, MMA, ...) and time (now, today, tomorrow, next 48 h)
- In the Live TV list you can switch the playlist (or "All playlists") without going into the settings
- Second stream in the live player: picture-in-picture or split screen, sound from either stream, swap streams. Arrangement, window size (4 sizes) and corner are saved (Settings > Player > Second stream)

**Phone search**
- New button at the bottom of the sidebar: shows a QR code. Scan it and search movies, series and live channels on your phone (a web page served by the TV, same Wi-Fi, nothing to install). Tap a result to play it on the TV; pause, skip, stop and volume buttons included

**Languages**
- Dutch (Nederlands) is the 10th language

**Metadata**
- Own API keys for TMDB and OMDb in Settings > API keys
- Without keys: description, cover, rating, genres, running time and trailer straight from the Xtream provider
- OMDb ratings also without TMDB (lookup by title and year)
- Rating order for sorting and "Top rated": IMDb, Rotten Tomatoes, TMDB, provider

**Interface (new)**
- Settings > View rebuilt: 5 tabs (cards, sidebar, grid, home, categories) with a live preview of every option
- New "Donate" card in the settings (PayPal link and QR code)

**Transfer**
- Transfer history, favorites and settings to another device over Wi-Fi (optionally with playlists)
- QR codes: scan instead of typing the address (add playlist from phone, Wi-Fi transfer, Google sign-in)
- Settings > About: optional donation link (PayPal) with QR code
- Much faster loading of large Xtream accounts: series episodes are fetched when a series is opened instead of 50,000 requests while loading
- Optional backup to Google Drive (sign-in with a code, see docs/GOOGLE_DRIVE.md)
- Read the last crash report on the phone (no clipboard needed)

## 1.0 - first open-source release
- Android TV / Google TV / Fire TV IPTV player for Xtream Codes and M3U playlists
- Home, Movies, Series, Live TV with TV guide and catch-up
- Cinema and grid browse views, filters, search with typo tolerance
- Player with audio/subtitle selection, FFmpeg audio decoder, skip intro/credits, source fallback
- Profiles, parental controls, history, my list, favourites, backup and restore
- 9 languages, 12 colour themes
- Licensed under GPL-3.0
