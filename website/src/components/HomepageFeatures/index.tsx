import clsx from "clsx";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
  title: string;
  description: JSX.Element;
};

const FeatureList: FeatureItem[] = [
  {
    title: "Simple",
    description: (
      <>
        Minimal API surface — send messages, register button handlers, and
        listen for interactions in a few lines of code.
      </>
    ),
  },
  {
    title: "Type-Safe",
    description: (
      <>
        Button values are typed with generics. Your handler receives a{" "}
        <code>ButtonClick[T]</code> — no stringly-typed plumbing.
      </>
    ),
  },
  {
    title: "Functional",
    description: (
      <>
        Built on Cats Effect and sttp. Bring your own backend — fs2, http4s, or
        anything sttp supports.
      </>
    ),
  },
];

function Feature({ title, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4")}>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): JSX.Element {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
