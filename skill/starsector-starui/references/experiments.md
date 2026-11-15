# Experiment rounds

When the user is unhappy with an element and the fix is not obvious, do not try the next tweak. Draw three
solutions that differ in kind, next to the current state, recommend one and let the user pick. One round costs
one board and settles what three rounds of nudging do not. The method below was taken from the rounds that
produced the approved pet screen ([worked rounds](#worked-rounds)).

Contents: [When to run one](#when-to-run-one) · [The round](#the-round) ·
[Finding the variants](#finding-the-variants) · [Recommending](#recommending) ·
[After the pick](#after-the-pick) · [Worked rounds](#worked-rounds) · [Faults](#faults)

## When to run one

Sort every change request on existing UI (a board or a built panel) before you touch anything:

| The user wrote | Do |
|---|---|
| A fault: overlap, cut off text, misaligned, a broken rule, a wrong colour role | Fix it. Show the before and after pair. No round. |
| A concrete instruction: "move Recall into the ship strip", "make it 120 px", "drop the bar" | Do it as asked, show the pair, and offer once in one line: `Say "experiment" and I draw three other ways to do this.` Offer only for changes of place, structure or picture, not for a label or a colour. |
| A complaint without a solution: "looks lost", "muddled", "not legible", "too much", "doesn't work", "meh" | Run a round. Do not ask first. |
| An invitation: "ideas?", "experiment", "try things", "be radical", "what else could this be?" | Run a round. For "be radical" drop the safe slot and draw two bold ones. |
| "Make it more vanilla", "doesn't look vanilla", "feels like mod UI" | Run a round, on the structure, not a tweak pass of fonts, buttons and sizes: each variant takes a different vanilla screen as its model ([gallery.md](gallery.md)) and rebuilds the block the way that screen lays out the same content. Bigger icons or vanilla's fonts on the same layout is not a variant. |
| The same element rejected a second time | Run a round. Stop adjusting. |
| A note that can be read two ways | Draw both readings and ask. This is not a round. |

The user can set this once ([mockups.md](mockups.md#ask-first)): rounds on complaints (default), on every
layout change, or only when asked.

One round is about one element or one block. Two complaints are two rounds on two boards.

## The round

1. **Now.** Get the current state: a screenshot, or the current layout rebuilt with the kit. It is the first
   cell of the board.
2. **Problem sentence.** One sentence, in the player's terms, from the user's own words: "Gene scan looks lost
   under the table." Not "the padding is too small": that is already a solution. If you cannot write the
   sentence, ask one question and stop.
3. **Answer the five questions** in [Finding the variants](#finding-the-variants). Write the answers down in
   one line each. They decide the variants and later the recommendation.
4. **List candidates.** Go through the moves table and write one line per move that gives something
   buildable. Aim for five or more lines before you draw anything.
5. **Pick three** (four at most) that pass the tests below, one per slot: safe, structural, bold.
6. **Draw** them with `k.experiment` ([mockups.md](mockups.md#helpers)): the same frame width, the same worst
   case data, numbered, the current state first. Draw only the affected block when nothing else moves, the
   whole panel when a variant moves things across it. Fix every layout warning. Every variant keeps the hard
   rules; bold means a different idea, not a broken rule.
7. **Caption each variant**: a name of two to four words, what was changed, what it costs.
8. **Recommend one** and ask one question: `Which one, or what bothers you about all of them?` With a
   decision card, the options are the variant names.
9. **Wait.** Nothing is built from a round before the pick.

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

The full source is `experiment()` in `scripts/mockup/example_board.py`; build it to see the board.

No pick ("none of these")? The answer to "what bothers you" is the new problem sentence. Run one more round
from it. After two rounds without a pick, ask the user for a sketch or for a screen, vanilla or not, that
does it right.

## Finding the variants

### Five questions

| # | Question | Answers |
|---|---|---|
| 1 | What is it? | a value, a state, an action, flavour, a relation between two things, a comparison across many, a warning |
| 2 | What does it belong to? | The thing it changes or describes. List every home the panel offers: the name bar, the entry in the list (row or tile), a strip of its subject, a table (as a row or a column), the right end of a heading, the bottom row, a tooltip, the popup, another tab, nowhere. |
| 3 | When does the player need it? | always, only while a state holds, only before a click, once |
| 4 | How much of it at once? | all of it, only the selected one, a summary with the rest on hover |
| 5 | What does the complaint point at? | see the next table |

| Complaint | Likely cause | Start with |
|---|---|---|
| "lost", "floating", "alone", "tacked on" | it has no owner | question 2: moves 1 and 2 |
| "muddled", "mixed up", "what belongs to what" | two kinds of thing share one block | question 1: move 3 |
| "not legible", "too much", "wall of numbers" | too much at once, or the wrong picture | question 4: moves 5 and 6 |
| "didn't see it", "can't find" | wrong place, or nothing points to it | moves 1 and 8 (a marker on the entry) |
| "too many buttons", "cluttered", "overloaded with options" | actions sit in a block, away from what they change | move 1 for every action: features in place ([buttons.md](buttons.md#a-panel-overloaded-with-options)). The safe slot is always the in place variant. |
| "too heavy", "takes too much room" | shown always, needed sometimes | question 3: moves 2 and 7 |
| "boring", "doesn't feel like the game" | no vanilla model behind it | move 9, then 4 |

### Moves

Each move gives a variant of a different kind. The same move twice is fine when the homes differ (in the
table, in the bottom row); the same home with another size or side is not a second variant.

| # | Move | What changes | Example |
|---|---|---|---|
| 1 | Other owner | It moves onto the thing it changes | Play moves from a button block onto the want strip |
| 2 | Fold into a part that exists | It becomes a row or column of the table, the cost on a button, the note at the end of a heading | The scan cost goes onto the Gene scan button, the unread traits become grey `?` rows of the table |
| 3 | Split | Two kinds in one block get one home each | Flavour becomes the tagline under the name, the want gets its own strip |
| 4 | Other part | The same content in another part from [parts.md](parts.md) | text to a bar, a number to pips, rows to tiles, a list to a table |
| 5 | Other picture | The form itself changes | an odds table becomes a flow chart; two parents with lanes between them |
| 6 | Less at once | One thing large, the rest reachable | traits as toggles, the chosen one drawn large; a summary with the detail in the tooltip |
| 7 | Only when it matters | It shows while its state holds | the want strip exists only while a want is open |
| 8 | Remove | Something already on the panel carries it | a marker on the tile, the colour of the state word, the tooltip |
| 9 | Borrow | How the vanilla screen with the same kind of content does it ([gallery.md](gallery.md)) | the officer picker's skill row, the industry picker's table |

### Three slots

- **Safe:** keep the element's form and fix the one property the complaint names. Usually move 1 or 2. This
  is the variant the user expects.
- **Structural:** another owner or another part. Moves 1 to 4.
- **Bold:** another picture, less at once, or gone. Moves 5 to 8. It must be one you would build. A variant
  drawn to lose is a wasted cell.

### Tests before drawing

- **Name test.** Each variant has a name of two to four words that says its idea: "In the table", "On the
  button", "Own strip". If two names differ only by a number or a side ("8 px" and "16 px", "left" and
  "right"), they are one variant. Replace one.
- **Rule test.** No hard rule of this skill is broken by any variant.
- **Data test.** All variants show the same information. Where one drops something, its cost line says so
  ("loses the red amount when you are short").
- **Worst state test.** Think each variant through in its worst state: short on stock, empty, 14 entries, a
  40 character name. A variant that fails there says so in its cost line or is replaced.
- **No filter.** Do not drop a variant because you like another one more. In the source rounds the user
  picked a variant other than the recommended one three times out of four. The recommendation is input.

## Recommending

- Give the reason from questions 1 and 2, not from taste: "The flavour is the pet's voice and belongs to its
  name. The want is an action and belongs with the other action box." Never "looks cleaner".
- Name one cost for each variant you did not recommend, and the cost of your own.
- Offer a mix in one line when two combine: "B, with C's picture as the tooltip of a trait."
- One question at the end, nothing after it.
- After the pick, do not argue for your variant. Build the pick.

## After the pick

1. Move the pick into every board, every state and the small screen.
2. Delete the experiment board and the variants that lost.
3. Add the states the pick creates. A cost that moved onto a button needs the short on stock state: grey
   button, red amount, tooltip with the stock.
4. Update the text board when wording changed.
5. Write the decision and its reason into the project's UI notes, so the next panel starts from it.
6. On built UI: change the code only now, then see it in game and show before and after
   ([verify.md](verify.md)).

## Worked rounds

From the design of a pet roster panel. Read them for the kind of difference between the variants.

| Complaint | What the element is | Variants | Recommended, picked | What followed |
|---|---|---|---|---|
| "Wants to play and the flavour text are muddled up with the top section. Ideas?" | Flavour and an action in one block | 1 split: tagline under the name, want as its own strip (move 3). 2 one bordered box for both (move 2). 3 a heading strip over both (move 4). | 1, 1 | The strip shows only while a want is open (move 7). |
| "Gene scan seems lost after the table"; an own strip was tried and rejected | An action with a cost that reveals table rows | 1 inside the table, a dashed block with cost and button (move 1). 2 the bottom row, cost on the button, unread rows as grey `?` (moves 2 and 8). 3 one line under the heading (move 4). 4 a table of its own (move 3). | 1, 2 | Short on stock state added: grey button, red amount, tooltip. |
| "That stat panel is not legible for any normal human ... be radical" | Odds: a relation between two parents and their young | A table with odds bars (safe). B lanes between the two parents (move 5). C a flow chart, traits as toggles, one trait drawn large (moves 5 and 6). | B with C as tooltip, C | Boards, states and small screen moved to C. A and B deleted. |
| A parent with young, drawn in a family tree | A relation that repeats | A the parent stands a second time next to its mate (move 3). B one long line from the single tile (move 5). | A, B | A stress case (circular inbreeding) was drawn in B before building. |

## Faults

- Three variants that are one layout with other padding, another colour or the other side.
- One favourite and two drawn to lose.
- Variants with different sample data, or without the current state next to them.
- A recommendation without a reason, or with "cleaner" as the reason.
- A list of questions after the board.
- More than four variants.
- The experiment board still on the page after the pick.
- A round for a fault or a broken rule. Those are fixed.
- Variants built in code. A round is boards only.
