"""What each class is for, in the app's own voice (PLAN 23.2.7).

**This is the only file in the library a build rule cannot check for truth.**
Everything else about a class is derived from its blocks — the title, the
duration, the shape sentence, the chart — so the library can tell a rider that
a ride is "20 min · Climbs · four hard efforts" and cannot tell them why they
would ride it, what it trains, or what it is going to feel like at minute
fourteen. That is authored knowledge, and this is where it lives.

Kept separate from `catalogue.py` deliberately, for two reasons. The catalogue
is written in blocks of real time and stays readable as blocks; and these 72
sentences have to be read **as a set** to be any good, because the failure mode
is not a wrong sentence, it is 72 sentences that all sound the same — a rider
scanning the library would learn to skip them within four classes.

`build.py`'s R13 holds the shape rather than the content:

- every class has one, and it is between 80 and 320 characters;
- it does not name its own duration, which is drawn beside it;
- it does not name its own category, which is also drawn beside it — and that
  ban is the most useful rule here, because it is what forces *"the hardest
  pace you could hold for about an hour"* instead of *"threshold"*. The plain
  sentence is the one a first-time rider can act on;
- no units and no acronyms (Phase 26): a rider choosing tonight's ride is
  deciding, not reading a measurement;
- and a description promising a position has to be describing blocks that ask
  for it, which is R11 and 25.4.2 arriving on a second surface.
"""

DESCRIPTIONS = {
    # ---------------------------------------------------------------- Endurance
    "END-01": (
        "A single steady block at a pace you could hold a conversation through. "
        "This is the ride that builds the base everything else sits on, and the "
        "discipline is keeping it easy when your legs say they could do more."
    ),
    "END-02": (
        "The effort stays put while your legs speed up underneath it, one step at "
        "a time. It teaches you that pace and pedal speed are two different "
        "dials, which is the first thing most riders find they had been "
        "treating as one."
    ),
    "END-03": (
        "Three blocks that get gradually harder without ever getting hard. A good "
        "first long ride: it finishes stronger than it starts, so you learn what "
        "your steady pace feels like when you are already a little tired."
    ),
    "END-04": (
        "Six short rises with a soft-pedal between them, ridden seated at a "
        "heavy, deliberate cadence. It is the shape of a road that never quite "
        "flattens out, and it trains the knack of keeping going over repeated "
        "small efforts."
    ),
    "END-05": (
        "One long block and nothing else. There is no interval to count down to, "
        "which is the point — this is the ride that teaches patience, and it is "
        "the best possible use of a film you have been meaning to watch."
    ),
    "END-06": (
        "Steady riding with brief lifts into a firmer pace, then straight back "
        "down. Short enough that nothing ever really hurts, frequent enough that "
        "your body learns to clear the effort and carry on without a long rest."
    ),
    "END-07": (
        "Four long blocks, each a little different in pedal speed, all at the "
        "same easy effort. Built for the days when the goal is time on the bike "
        "rather than a number, and for riders getting used to sitting still and "
        "working."
    ),
    "END-08": (
        "Five firm blocks at a heavy, slow cadence with a short spin between "
        "them. The effort is moderate; the load on your legs is not. Good "
        "preparation for real hills, and for anyone who finds slow, heavy work "
        "uncomfortable."
    ),
    "END-09": (
        "It starts easy and finishes firm, with each block harder than the one "
        "before. A progression is the most honest test of pacing there is: go "
        "too hard early and the last block will tell you about it."
    ),
    "END-10": (
        "Three long steady blocks and very little else to think about. This is "
        "the ride that does the quiet work — the kind that shows up months later "
        "as a higher pace for the same effort. Bring something to watch."
    ),
    "END-11": (
        "Long steady riding interrupted nine times by a brief firmer effort. The "
        "surges are short enough to recover from inside the steady block, so the "
        "ride stays comfortable while teaching your legs to change pace on "
        "demand."
    ),
    "END-12": (
        "Alternating heavy, slow blocks and light, fast ones at the same steady "
        "effort. It is a whole ride about pedal speed rather than pace, and the "
        "quickest way to find out which cadence you naturally default to."
    ),

    # ----------------------------------------------------------------- Recovery
    "REC-01": (
        "A short, gentle spin with one slightly firmer middle block. Nothing here "
        "is meant to be hard: the job is to move blood through tired legs and "
        "get off the bike feeling better than you got on."
    ),
    "REC-02": (
        "Easy riding with two brief bursts of fast, light pedalling in the "
        "middle. The bursts are about pedal speed rather than effort — they wake "
        "the legs up without asking anything of them."
    ),
    "REC-03": (
        "Easy throughout, with a slightly firmer block in the middle so the ride "
        "does not feel like nothing at all. The day after something hard, this is "
        "more use than rest and far more use than another hard ride."
    ),
    "REC-04": (
        "Light and fast from start to finish, never leaving the easiest zone. It "
        "is a ride for the legs rather than the lungs, and the only way to do it "
        "wrong is to push."
    ),
    "REC-05": (
        "Gentle riding with a single easy lift in the middle. Named for what it "
        "does — it moves the legs enough to clear yesterday out of them, and asks "
        "for nothing you will notice tomorrow."
    ),
    "REC-06": (
        "Half an hour of easy riding with one block a little firmer than the "
        "rest. A good default for a rest day when sitting still does not appeal, "
        "and a good first ride back after time off."
    ),
    "REC-07": (
        "Steady, unhurried riding with nothing to chase. The pace stays low "
        "enough to talk through and the blocks are long enough that you forget "
        "you are counting. Put something on and let it pass."
    ),
    "REC-08": (
        "Easy effort throughout, with the pedal speed changing every couple of "
        "minutes. It stays light the whole way — the variety is there to keep a "
        "gentle ride interesting, not to make it harder."
    ),
    "REC-09": (
        "A long, entirely easy ride. It looks like a lot of time for very little "
        "work, and that is exactly what it is for: the days between hard efforts "
        "are when you actually get fitter."
    ),
    "REC-10": (
        "Long and easy, broken into shorter blocks with small changes in pedal "
        "speed so it never becomes monotonous. Nothing here should raise your "
        "breathing. If it does, ease off."
    ),

    # -------------------------------------------------------------- Sweet Spot
    "SWT-01": (
        "Two firm blocks just below the hardest pace you could hold for an hour. "
        "Hard enough to build real fitness, easy enough that you could do it "
        "again tomorrow — which is what makes this the most productive effort in "
        "the whole library."
    ),
    "SWT-02": (
        "The effort steps just above and just below a firm pace without ever "
        "letting you settle. The easier stretches never quite feel like rest, and "
        "learning to recover while still working is the whole skill this trains."
    ),
    "SWT-03": (
        "Three firm blocks with an easy spin between them, at an effort you could "
        "just about hold for an hour. The classic session for building "
        "sustainable strength, and the one to repeat when you want to watch a "
        "number move."
    ),
    "SWT-04": (
        "Three blocks that get progressively harder, finishing at a genuinely "
        "firm effort. It gives you a gentle way in, which makes it a good "
        "introduction to sustained work if long hard blocks have put you off "
        "before."
    ),
    "SWT-06": (
        "The blocks get shorter as the effort gets harder. It front-loads the "
        "difficulty in length and back-loads it in intensity, so the last block "
        "is brief and unpleasant and the first is long and manageable."
    ),
    "SWT-07": (
        "Three long firm blocks at an effort just under your hardest sustainable "
        "pace. Longer than the classic version and correspondingly harder — this "
        "is the session that makes an hour on the bike feel easier."
    ),
    "SWT-08": (
        "Two sets of alternating harder and easier efforts, neither of them "
        "restful. It trains the ability to absorb a surge and carry on, which is "
        "what separates riders who can hold a pace from riders who can only "
        "average it."
    ),
    "SWT-09": (
        "Firm blocks ridden alternately at a heavy slow cadence and a light fast "
        "one. The effort is the same both ways; what changes is where you feel "
        "it. A good ride for finding out which of the two you are worse at."
    ),
    "SWT-10": (
        "The blocks grow, then shrink again, at a firm and sustainable effort. "
        "The middle is the hardest part and you can see it coming, which makes "
        "this as much a pacing exercise as a fitness one."
    ),
    "SWT-11": (
        "Three long firm blocks — the most sustained work in this part of the "
        "library. By the last one you will know exactly what your sustainable "
        "pace is, because anything above it will have become impossible."
    ),
    "SWT-12": (
        "Long firm blocks separated by easy riding rather than by rest, so the "
        "whole ride is working. It is a big session and it earns its length: "
        "this is the shape most structured training is built around."
    ),
    "SWT-13": (
        "Three growing blocks at a firm effort and a deliberately slow, heavy "
        "cadence. It builds strength through the pedal stroke, and it is "
        "uncomfortable in a way that has nothing to do with your breathing."
    ),

    # --------------------------------------------------------------- Threshold
    "THR-01": (
        "Two blocks at the hardest pace you could hold for about an hour. Short "
        "enough to be a manageable way into genuinely hard sustained work, and "
        "long enough to be unmistakably hard."
    ),
    "THR-02": (
        "It starts at the hardest pace you could hold for an hour and finishes "
        "above it, in efforts you can only sustain for a few minutes. A short "
        "ride with a sharp sting in the last third."
    ),
    "THR-03": (
        "Two long blocks at your hardest sustainable pace. There is nowhere to "
        "hide in a session this simple — the only decisions are how hard to start "
        "and whether you can hold it."
    ),
    "THR-04": (
        "The effort alternates just above and just below your hardest sustainable "
        "pace, with the harder stretches high enough to hurt. It trains your "
        "ability to clear the damage of a surge while still riding hard."
    ),
    "THR-05": (
        "Blocks that get shorter and harder as the ride goes on, all at or near "
        "your limit for sustained work. The reward for surviving the long first "
        "block is a shorter, nastier last one."
    ),
    "THR-06": (
        "Four hard blocks ridden at a slow, heavy cadence. The same effort as the "
        "usual version and a very different feeling: this one is limited by your "
        "legs long before it is limited by your lungs."
    ),
    "THR-07": (
        "Two very long blocks at your hardest sustainable pace. It is the closest "
        "thing here to a fitness test, and riders often use it as one — if you "
        "can hold both blocks evenly, the number the app is training you on is "
        "about right."
    ),
    "THR-08": (
        "Four hard blocks with a real rest between each. Breaking the work into "
        "four makes a big total achievable, and the rests are long enough that "
        "the last block should be as good as the first."
    ),
    "THR-09": (
        "Hard sustained blocks with a short, much harder effort stacked on top of "
        "each one. The sting at the end of each block is brief, and it is what "
        "makes this session harder than its length suggests."
    ),
    "THR-10": (
        "Five hard blocks with the recovery getting shorter each time. The work "
        "never changes and the ride gets harder anyway, which is a lesson about "
        "rest that most riders learn the hard way."
    ),
    "THR-11": (
        "Four long hard blocks over a full hour. This is a serious session — the "
        "sort that wants a day of easy riding after it — and the most direct way "
        "to raise the pace you can hold for a long time."
    ),
    "THR-12": (
        "Long hard blocks with shorter, sharper efforts mixed in among them. The "
        "variety keeps the hour interesting and covers two kinds of fitness in "
        "one ride, at the cost of doing neither in isolation."
    ),

    # ------------------------------------------------------------------ VO2 Max
    "VMX-01": (
        "Two efforts of three minutes at a pace you can only hold for a few "
        "minutes at a time. Short, brutal and over quickly — the gentlest "
        "possible introduction to the hardest kind of interval."
    ),
    "VMX-02": (
        "Half a minute on, half a minute off, over and over. The individual "
        "efforts feel manageable and the accumulation does not — by the second "
        "half you will be working hard just to keep starting them."
    ),
    "VMX-03": (
        "Efforts that grow from one minute to three, all at a pace you cannot "
        "hold for long. Starting short lets you find the right intensity before "
        "the intervals get long enough to punish getting it wrong."
    ),
    "VMX-04": (
        "Three very hard efforts of three minutes with full recovery between "
        "them. This is the session that raises your ceiling — the pace you can "
        "reach at all, rather than the pace you can hold."
    ),
    "VMX-05": (
        "Three efforts of four minutes at a pace you can only just sustain. A "
        "minute longer than the standard version, and that minute is where the "
        "whole difficulty of this session lives."
    ),
    "VMX-06": (
        "Forty-five seconds hard, forty-five seconds easy, repeated until it "
        "stops feeling short. The rest never quite covers the work, which is "
        "deliberate — the fatigue is meant to build."
    ),
    "VMX-07": (
        "Efforts that grow and then shrink again, all at a pace you cannot hold "
        "for long. The longest one sits in the middle, so the hardest moment "
        "arrives when you are already tired and still have work left."
    ),
    "VMX-08": (
        "Four efforts of four minutes at close to your maximum sustainable pace, "
        "with long recoveries. It is the best-studied hard interval session there "
        "is, and it is as unpleasant as that reputation suggests."
    ),
    "VMX-09": (
        "Very hard efforts ridden at a heavy, slow cadence. Putting maximum "
        "intensity together with low pedal speed asks a lot of your legs as well "
        "as your lungs, and it is the hardest ride here for most people."
    ),
    "VMX-10": (
        "Hard efforts of several different lengths in one ride, from under a "
        "minute to several. The variety means no single length gets a full dose, "
        "and it means you find out which one you are worst at."
    ),

    # ------------------------------------------------------------------- Climbs
    "CLB-01": (
        "Four short blocks at a deliberately slow, heavy cadence, ridden seated. "
        "It is a strength session more than a fitness one — the resistance does "
        "the work, and your legs will know about it before your breathing does."
    ),
    "CLB-02": (
        "Short, sharp efforts out of the saddle with easy riding between them. "
        "Getting out of the saddle changes which muscles do the work, and doing "
        "it well takes practice. This is where to get it."
    ),
    "CLB-03": (
        "One long block at a firm effort and a slow, heavy cadence, the way a real "
        "hill is ridden. No intervals to count and no rests to look forward to, "
        "which is exactly what a long ascent feels like."
    ),
    "CLB-04": (
        "Repeated seated rises with brief recoveries, ridden at a heavy cadence "
        "throughout. The shape of a road that keeps going up in steps, and a good "
        "ride for learning to recover without stopping."
    ),
    "CLB-05": (
        "Blocks that grow in length at a slow, heavy cadence. Each one asks you to "
        "hold the same grinding effort a little longer, which is the most direct "
        "way to build the strength a long hill needs."
    ),
    "CLB-06": (
        "Sustained seated work interrupted by short efforts out of the saddle. It "
        "is how a hill is actually raced — a steady grind with sudden "
        "accelerations, and the trick of recovering from those without losing the "
        "rhythm."
    ),
    "CLB-07": (
        "Four long blocks at a firm effort and a low cadence, with real recoveries "
        "between them. Long enough to build genuine strength on the hills, "
        "structured enough that the last block is still rideable."
    ),
    "CLB-08": (
        "Sets of heavy, slow-cadence work with short breaks inside each set and "
        "longer ones between them. The accumulated load is the point: no single "
        "block is dreadful and the total is considerable."
    ),
    "CLB-09": (
        "A long, varied ascent with changes of gradient, a spell out of the saddle "
        "and a harder section near the top. The closest this library gets to "
        "riding a real mountain, and paced like one."
    ),
    "CLB-10": (
        "An hour of sustained climbing with changing pitch, a stretch out of the "
        "saddle and a hard final section. The longest and most demanding ascent "
        "here, and the one to save for a day you have time for."
    ),

    # ------------------------------------------------------------------ Sprints
    "SPR-01": (
        "Two sets of eight maximal twenty-second efforts with ten seconds between "
        "them. It is the most famous interval protocol there is, it takes almost "
        "no time at all, and it is genuinely horrible."
    ),
    "SPR-02": (
        "Half a minute flat out, half a minute to recover, repeated. Longer than a "
        "true sprint and shorter than an interval, it sits in the gap where it "
        "hurts most and clears quickest."
    ),
    "SPR-03": (
        "Efforts that grow from a few seconds to the better part of a minute, each "
        "one ridden as hard as you can manage. Starting short lets you find your "
        "top end before the efforts get long enough to blunt it."
    ),
    "SPR-04": (
        "Three sets of eight maximal efforts, each twenty seconds long with barely "
        "any rest. Half again as much work as the standard version, and the third "
        "set is where the session is actually decided."
    ),
    "SPR-05": (
        "Three kinds of maximal effort in one ride — seated, out of the saddle, "
        "and wound up from a low speed. Each recruits something slightly "
        "different, and doing all three tells you which kind of fast you are."
    ),
    "SPR-06": (
        "Repeated flat-out efforts with incomplete recovery, so fatigue builds "
        "across the ride. It trains the ability to produce a second and a third "
        "effort after the first one has already emptied you."
    ),
}
