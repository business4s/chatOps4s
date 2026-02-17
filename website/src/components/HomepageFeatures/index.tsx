import clsx from "clsx";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
    title: string;
    description: JSX.Element;
};

const FeatureList: FeatureItem[] = [
    {
        title: "Minimal & Opinionated",
        description: (
            <>
                Pre-selects the feature set exposed by Slack to minimize mental overhead. One way to do buttons,
                commands, and forms — no decision fatigue.
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
        title: "Zero Infrastructure",
        description: (
            <>
                Runs over Socket Mode — no public URLs, no HTTP servers, no ingress.
                Built on sttp, works with any compatible backend.
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
