import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import React from "react";
import CodeBlock from "@theme/CodeBlock";

const SbtDependencies: React.FC = () => {
  const { siteConfig } = useDocusaurusContext();
  const version = siteConfig.customFields?.forms4sVersion;
  return (
    <CodeBlock className="language-scala">
      {`libraryDependencies ++= Seq(
    "io.github.chatops4s" %% "chatops4s-core" % "${version}", // Core functionality
)`}
    </CodeBlock>
  );
};

export default SbtDependencies;
