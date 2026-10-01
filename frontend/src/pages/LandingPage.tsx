import {
  ArrowDown,
  ArrowRight,
  Check,
  CircleAlert,
  Clock3,
  Languages,
  MoveUpRight,
  Sparkles,
  UsersRound,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { BrandMark } from '../components/BrandMark'
import '../landing.css'

/* ─────────────────────────────────────────────────────────
 * LANDING HERO STORYBOARD
 *
 * Static navigation and primary actions are always available.
 * Decorative hero layers add depth without delaying interaction.
 *
 *    0ms   navigation and hero copy are visible
 *  100ms   eyebrow and headline settle into place
 *  200ms   supporting copy and actions settle into place
 *  300ms   monitoring preview rises into place
 *  400ms   preview details finish their short cascade
 * ───────────────────────────────────────────────────────── */

const capabilities = [
  {
    number: '01',
    title: 'Capture real progress',
    description: 'Translators report what is complete and what remains, without breaking their working rhythm.',
    icon: Languages,
  },
  {
    number: '02',
    title: 'See pressure building',
    description: 'Editors and managers get a shared view of workload, review status, and approaching deadlines.',
    icon: Clock3,
  },
  {
    number: '03',
    title: 'Act before it is late',
    description: 'The team can focus attention where delivery needs it most, while there is still time to respond.',
    icon: MoveUpRight,
  },
]

const roles = [
  {
    initials: 'TR',
    title: 'Translator',
    description: 'A clear place for assigned activities, daily progress, and remaining-text estimates.',
  },
  {
    initials: 'CE',
    title: 'Chief Editor',
    description: 'A focused review queue with context for corrections and handoffs.',
  },
  {
    initials: 'PM',
    title: 'Project Manager',
    description: 'A portfolio-level view of staffing, workload, delivery signals, and risk.',
  },
]

export function LandingPage() {
  return (
    <main className="landing-page">
      <section className="landing-hero" aria-labelledby="landing-title">
        <div className="landing-hero__grid" aria-hidden="true" />
        <header className="landing-nav landing-container">
          <Link className="landing-brand-link" to="/" aria-label="EasyLang home">
            <BrandMark />
          </Link>

          <nav className="landing-nav__links" aria-label="Main navigation">
            <a href="#product">Product</a>
            <a href="#roles">For teams</a>
          </nav>

          <div className="landing-nav__actions">
            <Link className="button button--ghost landing-sign-in" to="/login">Sign in</Link>
            <Link className="button button--primary" to="/login">
              Sign in
              <ArrowRight size={17} aria-hidden="true" />
            </Link>
          </div>
        </header>

        <div className="landing-hero__content landing-container">
          <div className="landing-hero__copy">
            <div className="landing-eyebrow landing-reveal landing-reveal--one">
              <span className="landing-eyebrow__dot" aria-hidden="true" />
              Activity intelligence for translation teams
            </div>
            <h1 id="landing-title" className="landing-reveal landing-reveal--one">
              See the deadline<br />
              <span>before it sees you.</span>
            </h1>
            <p className="landing-hero__lead landing-reveal landing-reveal--two">
              EasyLang turns everyday activity updates into a clear view of delivery health—so your team can
              protect quality, balance work, and respond early.
            </p>
            <div className="landing-hero__actions landing-reveal landing-reveal--two">
              <Link className="button button--primary landing-button--large" to="/login">
                Open your workspace
                <ArrowRight size={18} aria-hidden="true" />
              </Link>
              <a className="landing-text-link" href="#product">
                Explore the flow
                <ArrowDown size={17} aria-hidden="true" />
              </a>
            </div>
            <div className="landing-trust landing-reveal landing-reveal--two" aria-label="Product principles">
              <span><Check size={15} aria-hidden="true" /> Role-aware access</span>
              <span><Check size={15} aria-hidden="true" /> Secure by design</span>
              <span><Check size={15} aria-hidden="true" /> Built for real workflows</span>
            </div>
          </div>

          <div className="landing-monitor landing-reveal landing-reveal--three" aria-label="Example delivery monitoring view">
            <div className="landing-monitor__topbar">
              <div>
                <span className="landing-monitor__overline">Delivery pulse</span>
                <strong>September workload</strong>
              </div>
              <span className="landing-live"><i aria-hidden="true" /> Live view</span>
            </div>

            <div className="landing-monitor__metric">
              <div>
                <span>Activities on track</span>
                <strong>18 <small>/ 24</small></strong>
              </div>
              <div className="landing-donut" aria-hidden="true">
                <span>75%</span>
              </div>
            </div>

            <div className="landing-chart" aria-hidden="true">
              <div className="landing-chart__labels"><span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span></div>
              <svg viewBox="0 0 520 144" role="presentation" focusable="false">
                <defs>
                  <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" stopColor="currentColor" stopOpacity="0.24" />
                    <stop offset="1" stopColor="currentColor" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <path className="landing-chart__area" d="M0 121 C54 117 72 78 126 84 S199 115 258 68 S348 38 390 55 S460 42 520 14 L520 144 L0 144 Z" />
                <path className="landing-chart__line" d="M0 121 C54 117 72 78 126 84 S199 115 258 68 S348 38 390 55 S460 42 520 14" />
                <circle cx="390" cy="55" r="5" />
              </svg>
            </div>

            <div className="landing-monitor__rows">
              <div className="landing-activity-row">
                <span className="landing-activity-row__icon landing-activity-row__icon--alert"><CircleAlert size={16} aria-hidden="true" /></span>
                <span><strong>Website localization</strong><small>Review needs attention</small></span>
                <span className="landing-status landing-status--attention">At risk</span>
              </div>
              <div className="landing-activity-row">
                <span className="landing-activity-row__icon"><Sparkles size={16} aria-hidden="true" /></span>
                <span><strong>Product handbook</strong><small>Progress updated today</small></span>
                <span className="landing-status">On track</span>
              </div>
            </div>
          </div>
        </div>

        <a className="landing-scroll-cue" href="#product" aria-label="Continue to product overview">
          <span>Scroll to discover</span>
          <ArrowDown size={16} aria-hidden="true" />
        </a>
      </section>

      <section className="landing-product" id="product" aria-labelledby="product-title">
        <div className="landing-container">
          <div className="landing-section-heading">
            <span className="landing-kicker">One shared signal</span>
            <h2 id="product-title">From scattered updates<br />to confident decisions.</h2>
            <p>
              EasyLang connects the activity lifecycle from first assignment to final review, giving each person
              the right level of clarity.
            </p>
          </div>

          <div className="landing-capabilities">
            {capabilities.map(({ number, title, description, icon: Icon }) => (
              <article className="landing-capability" key={number}>
                <div className="landing-capability__top">
                  <span>{number}</span>
                  <span className="landing-capability__icon"><Icon size={21} aria-hidden="true" /></span>
                </div>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>

          <div className="landing-flow" aria-label="Activity workflow overview">
            <div className="landing-flow__copy">
              <span className="landing-kicker">A living workflow</span>
              <h3>Every handoff stays visible.</h3>
              <p>
                Assignment, translation, review, and delivery belong to one connected flow—without forcing every
                role into the same screen.
              </p>
            </div>
            <div className="landing-flow__track">
              {['Assigned', 'In progress', 'In review', 'Delivered'].map((step, index) => (
                <div className="landing-flow__step" key={step}>
                  <span>{index + 1}</span>
                  <strong>{step}</strong>
                  {index < 3 && <i aria-hidden="true" />}
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="landing-roles" id="roles" aria-labelledby="roles-title">
        <div className="landing-roles__orb" aria-hidden="true" />
        <div className="landing-container landing-roles__content">
          <div className="landing-section-heading landing-section-heading--light">
            <span className="landing-kicker">Three perspectives. One delivery.</span>
            <h2 id="roles-title">Built around the people<br />who move language forward.</h2>
          </div>

          <div className="landing-role-grid">
            {roles.map((role) => (
              <article className="landing-role-card" key={role.title}>
                <span className="landing-role-card__avatar">{role.initials}</span>
                <div>
                  <h3>{role.title}</h3>
                  <p>{role.description}</p>
                </div>
              </article>
            ))}
          </div>

          <div className="landing-final-cta">
            <div>
              <span className="landing-final-cta__icon" aria-hidden="true"><UsersRound size={23} /></span>
              <p>Ready to bring calm to delivery?</p>
              <h2>Give every deadline<br />a better chance.</h2>
            </div>
            <Link className="button landing-button--light landing-button--large" to="/login">
              Sign in to EasyLang
              <ArrowRight size={18} aria-hidden="true" />
            </Link>
          </div>

          <footer className="landing-footer">
            <BrandMark compact />
            <p>Activity Monitoring for translation teams.</p>
            <div>
              <Link to="/login">Sign in</Link>
            </div>
          </footer>
        </div>
      </section>
    </main>
  )
}
