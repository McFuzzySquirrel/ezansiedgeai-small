# eZansiEdgeAI: Strategic Direction Reset & Forward Plan 

## Internal Research Summary and Proposed Execution Strategy  
  
Author: Doug McCusker    
Audience: Internal Team / Contributors / Technical Partners    
Date: February 2026    
Status: Strategic Direction Proposal (Based on Research + Technical Validation Thinking)  
  
---  
  
Over recent design and research discussions, it has become clear that our original mental model for eZansiEdgeAI needs refinement to match real-world constraints.  
  
This document captures:  
  
- What we have learned  
- Why we need to change direction slightly  
- The refined strategy  
- The practical execution plan  
- How this also becomes a teaching tool for students and developers  
  
This is not a pivot away from our mission.    
It is a maturation toward something deployable and impactful.  
  
  
  
## 2. Our Mission (Unchanged)  
  
Provide AI-powered educational support to learners who currently have limited or no access to advanced digital learning tools.  
  
Key realities we are designing for:  
  
- Expensive mobile data  
- Inconsistent connectivity  
- Low-end device availability  
- Large inequality gaps between schools and communities  
- Language diversity  
- Limited teacher time for individual learner support  
  
This is not a commercial profit-driven system.    
This is open, enabling infrastructure for inclusion in an AI world.  
  
  
  
## 3. What We Have Learned (Hard Truths)  
  
### 3.1 Cloud AI Is Not Built For These Environments  
Subscription-based AI assumes:  
- Reliable internet  
- Ongoing payments  
- Modern hardware  
- Centralized infrastructure  
  
Many schools and learners do not have these.  
  
  
### 3.2 Running “General AI” Locally On Cheap Devices Is Not Realistic (Yet)  
Low-end phones typically have:  
- 2–4GB RAM  
- Limited storage  
- Battery sensitivity  
- Thermal limits  
  
Trying to run large general models leads to:  
- Poor performance  
- Device overheating  
- Battery drain  
- User abandonment  
  
  
  
### 3.3 The Real Breakthrough Is Not Model Size  
The real opportunity is:  
  
Knowledge + Retrieval + Explanation, not raw model capability.  
  
  
  
## 4. Strategic Direction Adjustment  
  
### Old Implicit Direction  
“Offline AI assistant”  
  
### New Explicit Direction  
Offline Knowledge + Explanation Engine, Grounded in Curriculum Content  
  
This changes everything in a good way.  
  
  
  
## 5. Core Product Philosophy (Going Forward)  
  
### Offline First  
Must work fully offline after installation.  
  
  
  
### Phone First  
Primary learning happens on the learner’s phone.    
School infrastructure is optional enhancement, not dependency.  
  
  
  
### Retrieval First  
Knowledge comes from:  
Local curriculum content packs    
NOT model memory.  
  
  
  
### Content Is The Source Of Truth  
The model explains content.    
The model does not invent content.  
  
  
  
### Low Power By Design  
Must work on:  
Android 10+    
~3GB RAM devices    
  
  
### Ethical Personalisation  
We support:  
Learner-controlled learning preferences (local only)  
  
We do NOT support:  
Surveillance profiling    
Behaviour tracking    
Central learner analytics (V1)  
  
  
## 6. High-Level System Architecture  
  
### Phone (Primary Runtime)  
Handles:  
- Question input  
- Local content retrieval  
- Local explanation generation  
- Learner preference shaping  
- Local storage of packs  
  
  
  
### Optional School Node (WiFi Only)  
Provides:  
- Content pack updates  
- Speech services (future phase)  
- Optional heavier compute support  
  
Important:  
If node is offline → learning must still work.  
  
  
  
## 7. The Real Product Innovation  
  
Not:  
Offline LLM  
  
But:  
Offline Curriculum Knowledge Packs + Local Explanation Engine  
  
This allows us to scale knowledge distribution without scaling compute cost.  
  
  
## 8. Learner Personalisation — Our Ethical Position  
  
We will support:  
- Learner chooses explanation style  
- Learner chooses reading level  
- Learner chooses example type preference  
  
Stored:  
Locally only    
Editable by learner    
  
Not included:  
Performance tracking    
Behaviour modelling    
Cloud syncing    
  
  
## 9. Why This Direction Is Stronger  
  
This approach:  
  
✔ Works in low-connectivity environments    
✔ Works on cheaper hardware    
✔ Builds teacher trust (content grounding)    
✔ Reduces hallucination risk    
✔ Reduces data cost    
✔ Supports open-source ecosystem    
✔ Scales through content, not infrastructure    
  
  
## 10. V1 Execution Scope (Deliberately Narrow)  
  
### Initial Target  
Grade 6 Mathematics    
CAPS aligned    
Text interface first    
Offline only required    
  
  
### V1 Must Deliver  
- Ask question offline  
- Retrieve local curriculum content  
- Generate grounded explanation  
- Show worked example  
- Support learner explanation preferences  
- Load versioned content packs  
- Optional LAN pack updates  
  
  
  
### V1 Explicitly Will NOT Include  
- Voice-first interface  
- Cloud dependency  
- Behaviour analytics  
- Teacher dashboards  
- Multi-subject orchestration  
- Advanced agent systems  
  
  
  
## 11. 90 Day Execution Plan Summary  
  
### Phase 0 — Feasibility (Weeks 1–2)  
Validate:  
- On-device inference viability  
- Local embedding + retrieval  
- Storage footprint limits  
  
  
  
### Phase 1 — Offline Learning Loop (Weeks 3–6)  
Deliver:  
- Basic Android app  
- Pack loading  
- Retrieval + explanation pipeline  
  
---  
  
### Phase 2 — Content + Personalisation (Weeks 7–10)  
Deliver:  
- Learner preference engine  
- Pack builder tooling  
- First real Grade 6 pack  
  
### Phase 3 — School Node + Hardening (Weeks 11–13)  
Deliver:  
- LAN node discovery  
- Pack sync over WiFi  
- Battery + reliability testing  
  
  
  
## 12. How This Also Becomes A Teaching Platform  
  
This project demonstrates to students:  
  
Technology success is about:  
- Problem understanding  
- Constraints  
- Intent clarity  
- Architecture discipline  
  
Not:  
“Biggest model wins”  
  
  
## 13. Why We Are Making This Change Now  
  
Because building the wrong thing faster is still failure.  
  
We now have enough research to say confidently:  
  
If we optimize for:  
Offline + Content + Retrieval + Explanation  
  
We can realistically deploy something useful.  
  
  
## 14. What Success Looks Like in the Real World  
  
Success is NOT:  
A flashy demo.  
  
Success IS:  
A learner uses this offline    
On a normal phone    
For real homework    
Without help from us    
  
## 15. Strategic Long-Term Potential  
  
If V1 succeeds, we can expand to:  
  
- More grades  
- More subjects  
- More languages  
- Voice interfaces  
- Teacher tooling  
- Community-authored content packs  
  
But only after proving real-world value.  
  
## 16. Final Strategic Statement  
  
We are not building:  
A showcase of advanced AI.  
  
We are building:  
Reliable, offline, curriculum-grounded AI learning support    
For real classrooms    
On real devices    
In real inequality environments    
  
## 17. Next Internal Steps  
  
Recommended immediate actions:  
  
1. Align team on revised philosophy  
2. Lock V1 scope (do not expand early)  
3. Begin Phase 0 technical spikes  
4. Start Grade 6 pack content sourcing strategy  
5. Define pack authoring workflow  
  
## Closing Note  
  
This research has helped clarify something important:  
  
The future impact of AI in education for all, will not only be decided by model capability but by how it can be usable in the hardest environments.  
  
That is where we are choosing to focus.  
  
---  
  
# Going Forward  
Decision record + design philosophy + build blueprint.  
  
This section captures:  
	•	Recap of the original vision for eZansiEdgeAI  
	•	The reality checks and challenges discovered during design  
	•	The strategic pivot toward a **realistic, deployable, South Africa-first architecture**  
	•	The execution plan going forward  
	•	How this becomes a reusable **teaching framework for planning, documentation, and AI-assisted building**  
  
This is not just a technical spec.  
This is a **decision record + design philosophy + build blueprint**.  
  
  
**2. Original Vision**  
  
The original goal was ambitious and optimistic:  
  
Provide AI-powered learning assistance to underserved schools using local hardware and open models.  
  
Initial ideas included:  
	•	Raspberry Pi classroom devices  
	•	Subject-specific local SLMs  
	•	Offline document ingestion  
	•	Conversational tutoring  
	•	Multi-device classroom coordination  
	•	Fully open-source stack  
	•	Zero subscription dependency  
  
The moral driver was clear:  
  
If learners have nothing, they should still have *something*.  
  
  
**3. Reality Check – What We Challenged**  
  
We deliberately stress-tested the idea.  
  
**3.1 Hardware Reality**  
  
Problems discovered:  
	•	Raspberry Pi availability fluctuates  
	•	Hardware maintenance is hard in rural deployments  
	•	Physical theft / failure risk  
	•	Scaling hardware per classroom is expensive operationally  
  
**3.2 Model Reality**  
  
Problems discovered:  
	•	Good models are still compute heavy  
	•	Local fine-tuning is unrealistic in low-resource environments  
	•	Language support for African languages is uneven  
	•	Storage footprint matters on low-end devices  
  
  
**3.3 Power & Connectivity Reality (South Africa Specific)**  
  
Constraints we must design for:  
	•	Load shedding  
	•	Intermittent connectivity  
	•	Limited or zero school IT support  
	•	Expensive mobile data  
	•	Mixed device quality (very low → mid tier phones)  
  
**3.4 Deployment Reality**  
  
What kills most “good intention tech”:  
	•	No update mechanism  
	•	No support structure  
	•	Too complex for schools to operate  
	•	Requires cloud accounts or payment methods  
	•	Needs training  
  
  
**3.5 Adoption Reality**  
  
Even free tech fails if:  
	•	It is complicated  
	•	It drains battery  
	•	It needs setup  
	•	It feels slow  
	•	It breaks trust (privacy / tracking fears)  
  
  
**4. The Strategic Pivot**  
  
**From:**  
  
Device-first, classroom SLM compute nodes  
  
**To:**  
  
Phone-first, offline-first, micro-infrastructure assist  
  
This is a big shift.  
  
  
**5. New Core Principles**  
  
**5.1 Phone Is The Primary Compute Surface**  
  
Because phones are:  
	•	Already owned  
	•	Already charged  
	•	Already trusted  
	•	Already familiar  
  
  
**5.2 Offline First Always**  
  
System must work:  
	•	Without internet  
	•	Without accounts  
	•	Without cloud dependency  
  
  
**5.3 School Device = Capability Booster, Not Brain**  
  
Instead of hosting models:  
  
School device provides:  
	•	Speech to Text  
	•	Text to Speech  
	•	Content distribution  
	•	Optional heavier inference  
	•	Update distribution  
  
  
**5.4 Zero Cost To School To Run**  
  
Must run:  
	•	On existing WiFi router  
OR  
	•	On cheap shared edge device  
OR  
	•	Direct phone-to-phone  
  
  
**5.5 Fully Open Source**  
  
This is not optional. Strategic requirement.  
  
  
**6. Target Architecture**  
  
  
**6.1 Learner Phone Stack**  
  
  
```
App Layer
 ├ Learning UI
 ├ Chat Interface
 ├ Voice UI
 ├ Personal Learning Profile
 └ Local Content Library

AI Layer
 ├ Quantized Small LLM (GGUF / ONNX)
 ├ Embeddings (tiny model)
 └ Prompt Templates

Data Layer
 ├ Local Vector DB
 ├ Encrypted Learner Profile
 └ Offline Content Packs

Hardware Layer
 ├ CPU / NPU if available
 ├ 3–6GB RAM target
 └ Offline Storage

```
  
  
**6.2 School Edge Device Stack**  
  
```
WiFi Service Layer
 ├ Local API Gateway
 ├ Content Distribution
 └ Update Server

AI Services
 ├ Shared STT
 ├ Shared TTS
 ├ Optional Larger Model

Data Services
 ├ Shared Content Cache
 ├ School Knowledge Base

```
  
  
**7. The Personal Learning Profile**  
  
Instead of tracking performance like big EdTech platforms:  
  
We capture **learning preference signals**:  
  
Examples:  
	•	Visual vs Text vs Audio preference  
	•	Explanation depth preference  
	•	Language preference  
	•	Pace preference  
	•	Confidence feedback signals  
  
Stored:  
	•	Locally first  
	•	Exportable by learner  
	•	Never centrally monetised  
  
  
**8. Language Strategy**  
  
Short Term:  
	•	English base  
	•	Afrikaans support  
	•	Selected African language phrase layer  
  
Medium Term:  
	•	Community dataset contribution  
	•	Teacher assisted corpus building  
	•	On-device phrase translation fallback  
  
  
**9. Deployment Modes**  
  
**Mode A – Phone Only**  
  
Worst case fallback. Still usable.  
  
  
**Mode B – Phone + School Edge WiFi**  
  
Primary target deployment.  
  
  
**Mode C – Community Hub Device**  
  
Library / NGO / shared location.  
  
  
**10. V1 Scope**  
  
**Must Have**  
	•	Offline chat tutoring  
	•	Offline content pack loading  
	•	Local learner preference profile  
	•	WiFi discovery of school node  
	•	Basic STT via school node  
	•	Basic TTS via school node  
  
  
**Nice To Have**  
	•	Peer content sharing  
	•	Offline multiplayer learning games  
	•	Teacher dashboard (local only)  
  
  
**11. What We Explicitly Are NOT Building initially**  
  
	•	Cloud dashboards  
	•	Central learner analytics  
	•	Real-time internet AI augmentation  
	•	Payment / subscription anything  
	•	Heavy model fine tuning  
  
    
**12. Teaching Value**  
  
This project is also a **teaching artifact**.  
  
Students can learn:  
	•	Why constraints matter  
	•	Why architecture is trade-offs  
	•	Why documentation enables teams  
	•	How AI can build from clear specs  
	•	Why “perfect solution” does not always equal a deployable solution  
  
  
**13. AI-Assisted Development Experiment**  
  
We will test:  
  
Can powerful AI coding engines act like a project team  
if given **clear intent + constraints + architecture + backlog**?  
  
This repo becomes:  
	•	A spec  
	•	A lab  
	•	A teaching framework  
	•	A reference architecture  
  
    
**14. Risks **  
  
**Technical**  
	•	On-device model performance variance  
	•	Battery impact  
  
**Social**  
	•	Teacher adoption  
	•	Training requirements  
  
**Operational**  
	•	Update distribution  
	•	Device lifecycle management  

   
**15. Success Metrics**  
  
We care about:  
	•	Works offline reliably  
	•	Runs on low-mid Android devices  
	•	Teachers can install without IT support  
	•	Learners can use without training  
	•	No cost barrier  
  
  
**16. Long Term Vision**  
  
If successful:  
	•	Community contributed content packs  
	•	Language expansion via dataset growth  
	•	Hybrid edge/cloud optional extension  
	•	Integration with future low-cost AI accelerators  
  
  
**17. Final Philosophy**   
  
In an AI future, we need to ensure that **no learner is locked out simply because of the situation they find themselves in.**  
  
The success of this project is not baeed on how pwerful it is, but because it is the most **realistic, accessible, and deployable**.  
  
New Repository Structure  
  
ezansiedgeai/  
│  
├ README.md  
│  
├ docs/  
│ ├ architecture/  
│ │ ├ system-overview.md  
│ │ ├ phone-architecture.md  
│ │ ├ edge-device-architecture.md  
│ │ └ deployment-modes.md  
│ │  
│ ├ product/  
│ │ ├ vision.md  
│ │ ├ constraints.md  
│ │ ├ success-metrics.md  
│ │ └ user-personas.md  
│ │  
│ ├ adr/  
│ │ ├ ADR-000-template.md  
│ │ ├ ADR-001-phone-first-architecture.md  
│ │ ├ ADR-002-offline-first-design.md  
│ │ └ ADR-003-edge-device-as-accelerator.md  
│ │  
│ └ development/  
│   ├ coding-principles.md  
│   ├ ai-agent-instructions.md  
│   └ backlog-v1.md  
│  
├ apps/  
│ ├ learner-mobile/  
│ └ school-edge-node/  
│  
├ models/  
│ ├ phone-models/  
│ └ edge-models/  
│  
└ tools/  
  ├ content-pack-builder/  
  └ dataset-tools/  
