# The Devoxx Coffee Factory: a conveyor belt love story

Storyboard for the `coffee-factory` scene (code in
[`CoffeeFactory.java`](../src/main/java/io/github/rolfdobbelaere/ledart/CoffeeFactory.java)).
The chosen version for the raffle is `output/CHOSEN_coffee-factory_v2-smooth.gif`.

## The original idea

> From black I want to have a DEVOXX coffee cup placed by a robot hand on a 2D conveyor band. The cup moves to the
> right (but it stays in the middle because the camera is actually moving) and it's passing 3 dispensers: first "Java",
> which dispenses brown/black coffee; then it proceeds (the coffee slightly sloshes a drop out because it's moving) and
> stops at Gemini, which dispenses a colorful slushy-like substance, so the cup now has a rainbow ice-cream-like
> topping; then it moves on and stops at DEV (heart icon) and pink sprinkles are put on top (small heart icons appear
> on the side). The cup continues, the creamy topping is jiggling, and a hand takes it away, leaving a heart, fading to
> black, and the animation restarts.

Follow-up feedback that shaped v2: the travel between dispensers should take about one second, with a one-second
pause before and after each pour, so viewers can see what each dispenser is and what it represents.

## The story

A plain DEVOXX cup goes through the three ingredients of Devoxx: **Java** (the coffee and the community's roots),
**Gemini** (AI adding color and magic on top) and **DEV ♥** (the love developers put in). What comes out is taken
away and leaves only love behind.

## Storyboard (v2, about 20 s)

| Time (s) | Beat | What you see |
|---|---|---|
| 0.0 – 0.4 | Black | Darkness. |
| 0.4 – 1.2 | Placement | The conveyor fades in. A robot claw (fingertips in Google blue and red) lowers the white DEVOXX cup with its orange sleeve; it slows down as it lands. |
| 1.2 – 1.9 | Release | The claw opens and pulls back up. |
| 1.9 – 2.9 | Travel | The camera glides right: belt treads and rollers scroll, the cup stays centred. |
| 2.9 – 3.9 | Look: JAVA | The orange **JAVA** dispenser hangs above the cup; its status light is red. |
| 3.9 – 5.1 | Pour | The light turns green; a brown stream pours with little splashes, and the cup fills to a crema-ringed coffee. |
| 5.1 – 6.1 | Admire | Steam curls up from the fresh coffee. |
| 6.1 – 7.1 | Travel | As the cup accelerates, the coffee sloshes backwards and **a drop flies out**, splatting on the belt and riding away with it. |
| 7.1 – 8.1 | Look: Gemini | The dark Gemini dispenser, its four-pointed sparkle pulsing in the Gemini gradient. |
| 8.1 – 9.5 | Pour | A rainbow stream builds a **soft-serve swirl**, roll by roll, in Google colors plus Gemini violet. |
| 9.5 – 10.5 | Admire | The finished rainbow topping. |
| 10.5 – 11.5 | Travel | The topping leans back as the cup moves and wobbles when it stops. |
| 11.5 – 12.5 | Look: DEV ♥ | The pink **DEV ♥** dispenser; its heart beats. |
| 12.5 – 13.5 | Pour | Pink and white sprinkles rain down onto the swirl. |
| 13.5 – 14.5 | Admire | Little hearts float up on both sides of the cup and fade. |
| 14.5 – 16.3 | Exit and jiggle | The cup rolls on; when it stops, the topping **jiggles** (squash and stretch). |
| 16.3 – 17.6 | Pickup | The claw comes down with long fingers, grips the cup and lifts it out of view. |
| 17.4 – 18.6 | Heart | A heart pops up where the cup stood and wobbles. |
| 18.6 – 19.4 | Fade | Heart, belt and dispensers fade to black. |
| 19.4 – 19.8 | Black | The loop restarts. |

## Versions

| File | Frames | Notes |
|---|---|---|
| `CHOSEN_coffee-factory_v2-smooth.gif` | 137 | About 10 fps while moving; chosen, since the booth display plays long GIFs. |
| `coffee-factory_v2-pixoo30.gif` | 30 | Same timeline in 30 frames, for a real Pixoo 64. |
| `coffee-factory_v1-fast.gif` | 30 | First version, about 3.6 s: too fast to read the dispensers. |
