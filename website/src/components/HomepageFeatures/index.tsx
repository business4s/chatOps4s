import clsx from "clsx";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
    title: string;
    description: JSX.Element;
};

const FeatureList: FeatureItem[] = [
    {
        title: "Minimal",
        description: (
            <>
                The full API fits on one trait. One way to do buttons, commands, and forms. Sane defaults and minimal
                mental overhead. With sttp and circe as only dependencies.
            </>
        ),
    },
    {
        title: "Type-Safe",
        description: (
            <>
                Buttons carry typed values. Commands parse into case classes. Forms derive from data types.
                The compiler catches mistakes before Slack does.
            </>
        ),
    },
    {
        title: "High-Level",
        description: (
            <>
                Allows to create real-world apps with minimal effort. Adds features such as idempotency, user
                cache or app management approach.
            </>
        ),
    },
];

function Feature({title, description}: FeatureItem) {
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
