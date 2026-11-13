# StarUIFramework

A modern UI framework for Starsector built with a Kotlin DSL. Very WIP, has many missing features. Ping @Starficz on USC Discord for help if you want to use this for "real".

### Currently done for layout:
- CustomPanels (absolute/relative anchoring)
- Columns, Rows and Boxes (Compose-style alignment, weights, margin and padding)
- Sections (vanilla-style panels with a section heading)
- Clipping and scrolling on any container, per axis (`overflow`), with the mouse wheel for nested and horizontal scrolling
- Lazy lists (LazyColumn, LazyRow) for thousands of rows
  
Need to do:
- Grids 


### Currently done for content:
- Text
- Images
- Tooltips
- Buttons (with groups/multiselect)
- Checkboxes
- Text Fields
- Ship Displays
- Sliders and progress bars (one value; vanilla has no range slider)
- Section headings (vanilla's own)
- Tables (text, styled and custom cells, row tooltips, row clicks)
- Stat grids and stat modifier grids
- Labelled values, relationship bars, importance indicators
- Commodity icons, cost displays (campaign only)
- Ship lists, fleet summaries, codex entry links, cargo lists
- Planet displays
- Campaign content: skill panels, event progress bars, survey requirements, sector maps, the playthrough record

Need to do:
- Large Skill Icons
- Table rows with a glow (`addRowWithGlow`)
