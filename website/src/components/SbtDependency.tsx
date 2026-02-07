import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import React from "react";
import CodeBlock from "@theme/CodeBlock";

interface SbtDependencyProps {
  moduleName: "chatops4s-slack";
}

const SbtDependency: React.FC<SbtDependencyProps> = ({ moduleName }) => {
  const { siteConfig } = useDocusaurusContext();
  const version = siteConfig.customFields?.chatops4sVersion;
  return (
    <CodeBlock className="language-scala">
      {`"org.business4s" %% "${moduleName}" % "${version}"`}
    </CodeBlock>
  );
};

export default SbtDependency;
