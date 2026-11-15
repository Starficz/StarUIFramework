# Experiment rounds

When the user is unhappy with an element and the fix isn't obvious, don't tweak: draw three solutions that differ in
kind beside the current state, recommend one, let the user pick. One board settles what three rounds of nudging don't.
Method from the rounds behind the approved pet screen ([worked rounds](#worked-rounds)).

Contents: [When to run one](#when-to-run-one) · [The round](#the-round) ·
[Finding the variants](#finding-the-variants) · [Recommending](#recommending) ·
[After the pick](#after-the-pick) · [Worked rounds](#worked-rounds) · [Faults](#faults)

## When to run one

Sort every change request on existing UI first:

| The user wrote | Do |
|---|---|
| A fault: overlap, cut text, misaligned, a broken rule, a wrong colour role | Fix it, show the pair. No round. |
| An instruction: "move Recall into the ship strip", "make it 120 px", "drop the bar" | Do it, show the pair, offer once: `Say "experiment" and I draw three other ways to do this.` (only for place, structure or picture changes) |
| A complaint without a fix: "looks lost", "muddled", "not legible", "too much", "meh" | Run a round without asking. |
| An invitation: "ideas?", "experiment", "be radical" | Run a round; "be radical": drop the safe slot, two bold ones. |
| "Make it more vanilla", "feels like mod UI" | A round on structure: each variant copies a different vanilla screen's layout ([gallery.md](gallery.md)). Fonts or icon sizes on the same layout are not a variant. |
| The same element rejected twice | Run a round; stop adjusting. |
| A note readable two ways | Draw both readings, ask. Not a round. |

The user can set this once ([mockups.md](mockups.md#ask-first)): on complaints (default), on every layout change, or
only when asked. One round per element or block; two complaints, two boards.

## The round

1. **Now**: the current state (capture or rebuilt layout) as the first cell.
2. **Problem sentence** in the player's terms, from the user's words ("Gene scan looks lost under the table"), not a
   solution ("padding too small"). Can't write it: ask one question and stop.
3. **Answer the five questions** below, one line each.
4. **List candidates** from the moves table: five or more buildable lines before drawing.
5. **Pick three** (four at most) passing the tests: safe, structural, bold.
6. **Draw** with `k.experiment` ([mockups.md](mockups.md#helpers)): same frame width and worst-case data, numbered,
   current first; only the block when nothing else moves, else the whole panel; no layout warnings; hard rules kept
   (bold is a different idea, not a broken rule).
7. **Caption**: a two-to-four-word name, what changed, what it costs.
8. **Recommend one**, then ask only: `Which one, or what bothers you about all of them?`
9. **Wait**: nothing is built before the pick.

```python
cells, pick = k.experiment(
    '"Accelerate looks lost under the bar."',          # the problem sentence
    block('now'),                                      # the current state, same data as the variants
    [('On the bar', 'other owner: the days left', block('bar'), 'The bar is 132 px shorter.'),
     ('In the bottom row', 'other owner: the rare actions', block('bottom'), 'Far from the bar it changes.'),
     ('Strip with the result', 'other picture: the result is drawn', block('strip'), '48 px more height.')],
    recommend=0, reason='Accelerate changes the days left and nothing else.')
board = k.board('experiment_accelerate', 'Experiment: where does Accelerate live?', k.sidebyside(*cells, width=904), k.notes(pick))
```

Full source: `experiment()` in `scripts/mockup/example_board.py`.

No pick: the answer to "what bothers you" is the new problem sentence; one more round. After two rounds without a
pick, ask for a sketch or a screen (vanilla or not) that does it right.

## Finding the variants

### Five questions

| # | Question | Answers |
|---|---|---|
| 1 | What is it? | value, state, action, flavour, relation between two things, comparison across many, warning |
| 2 | What does it belong to? | The thing it changes or describes. Every home the panel offers: name bar, list entry, its subject's strip, a table row or column, a heading's right end, bottom row, tooltip, popup, another tab, nowhere |
| 3 | When is it needed? | always, while a state holds, before a click, once |
| 4 | How much at once? | all, only the selected one, a summary with the rest on hover |
| 5 | What does the complaint point at? | see below |

| Complaint | Likely cause | Start with |
|---|---|---|
| "lost", "floating", "tacked on" | no owner | question 2: moves 1, 2 |
| "muddled", "what belongs to what" | two kinds in one block | question 1: move 3 |
| "not legible", "too much", "wall of numbers" | too much at once, or the wrong picture | question 4: moves 5, 6 |
| "didn't see it" | wrong place, nothing points to it | moves 1, 8 (a marker) |
| "too many buttons", "cluttered" | actions in a block, away from their subjects | move 1 for each: features in place ([buttons.md](buttons.md#a-panel-overloaded-with-options)); safe slot = in place |
| "too heavy", "too much room" | always shown, sometimes needed | question 3: moves 2, 7 |
| "boring", "doesn't feel like the game" | no vanilla model | move 9, then 4 |

### Moves

Each gives a different kind of variant; one move twice is fine with different homes, not with another size or side.

| # | Move | What changes | Example |
|---|---|---|---|
| 1 | Other owner | Onto the thing it changes | Play moves onto the want strip |
| 2 | Fold into an existing part | A table row or column, the cost on a button, a heading's end note | Scan cost onto the Gene scan button; unread traits as grey `?` rows |
| 3 | Split | Two kinds get one home each | Flavour becomes a tagline; the want its own strip |
| 4 | Other part | Same content, another [part](parts.md) | text to bar, number to pips, rows to tiles, list to table |
| 5 | Other picture | The form changes | odds table to flow chart; lanes between two parents |
| 6 | Less at once | One thing large, the rest reachable | traits as toggles, chosen one large; summary plus tooltip |
| 7 | Only when it matters | Shown while its state holds | want strip only while a want is open |
| 8 | Remove | Something existing carries it | marker on the tile, state-word colour, tooltip |
| 9 | Borrow | The vanilla screen with the same content ([gallery.md](gallery.md)) | officer picker's skill row; industry picker's table |

### Three slots

- **Safe**: the element's form, fixing the one property named (usually move 1 or 2); what the user expects.
- **Structural**: another owner or part (moves 1 to 4).
- **Bold**: another picture, less at once, or gone (moves 5 to 8); one you would build, never drawn to lose.

### Tests before drawing

- **Name**: two-to-four-word names stating the idea ("In the table", "Own strip"); names differing only by a number
  or side are one variant.
- **Rules**: no variant breaks a hard rule.
- **Data**: all show the same information; a dropped item is in the cost line.
- **Worst state**: short on stock, empty, 14 entries, 40-char name; failures go in the cost line or get replaced.
- **No filter**: don't drop variants you like less; the user picked a non-recommended one three times in four.

## Recommending

- Reason from questions 1 and 2, never taste ("The flavour is the pet's voice and belongs to its name"; never
  "cleaner").
- One cost per other variant, and your own's.
- Offer a mix in one line when two combine ("B, with C's picture as a trait tooltip").
- One question at the end; after the pick, build it without arguing.

## After the pick

1. The pick into every board, state and the small screen.
2. Delete the experiment board and losing variants.
3. Add states the pick creates (a cost on a button needs short-on-stock: grey, red amount, stock tooltip).
4. Update the text board.
5. Record the decision and reason in the project's UI notes.
6. Built UI: change the code now, check in game, show before and after ([verify.md](verify.md)).

## Worked rounds

From a pet roster panel; read for the kind of difference between variants.

| Complaint | What the element is | Variants | Recommended, picked | What followed |
|---|---|---|---|---|
| "Wants to play and the flavour text are muddled up with the top section. Ideas?" | Flavour and an action in one block | 1 split: tagline, want strip (move 3). 2 one bordered box (move 2). 3 heading strip over both (move 4). | 1, 1 | Strip only while a want is open (move 7). |
| "Gene scan seems lost after the table"; an own strip was rejected | Costed action revealing rows | 1 in the table, dashed block with cost and button (move 1). 2 bottom row, cost on button, unread rows grey `?` (moves 2, 8). 3 a line under the heading (move 4). 4 own table (move 3). | 1, 2 | Short-on-stock state added. |
| "That stat panel is not legible for any normal human ... be radical" | Odds between two parents and young | A table with odds bars (safe). B lanes between parents (move 5). C flow chart, traits as toggles, one large (moves 5, 6). | B with C as tooltip, C | Everything moved to C; A, B deleted. |
| A parent with young in a family tree | A repeating relation | A the parent drawn again by its mate (move 3). B one long line from the single tile (move 5). | A, B | Circular inbreeding drawn in B before building. |

## Faults

- Three variants that are one layout with other padding, colour or side.
- One favourite and two drawn to lose.
- Different sample data, or no current state beside them.
- No reason, or "cleaner".
- A list of questions after the board.
- More than four variants.
- The experiment board left after the pick.
- A round for a fault (fix it).
- Variants built in code (boards only).
